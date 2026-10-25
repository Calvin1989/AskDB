package com.askdb.core;

import com.askdb.config.AskDbProperties;
import com.askdb.core.AskProgress.Step;
import com.askdb.core.AskResult.Status;
import com.askdb.core.AskResult.Trace;
import com.askdb.examples.Example;
import com.askdb.examples.ExampleStore;
import com.askdb.glossary.GlossaryService;
import com.askdb.glossary.Term;
import com.askdb.llm.GeneratedSql;
import com.askdb.llm.GeneratedSql.TokenUsage;
import com.askdb.llm.GenerationRequest;
import com.askdb.llm.PromptSections;
import com.askdb.llm.SqlGenerator;
import com.askdb.schema.SchemaContext;
import com.askdb.schema.SchemaProvider;
import com.askdb.sql.QueryResult;
import com.askdb.sql.SqlExecutor;
import com.askdb.sql.UnsafeSqlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 一次提问的总流程。这里只负责按顺序调用各步骤，具体逻辑都在各自的类里：
 * <ol>
 *   <li>查缓存（AnswerCache），命中直接返回</li>
 *   <li>匹配业务术语（GlossaryService）</li>
 *   <li>选表（SchemaProvider），术语涉及的表一定带上</li>
 *   <li>找相似问题的已确认示例（ExampleStore）</li>
 *   <li>生成 SQL（SqlGenerator）</li>
 *   <li>安全检查并执行（SqlExecutor）；报错时把报错交给模型修正，最多 N 次</li>
 *   <li>写缓存、记调用日志（QueryLogService）</li>
 * </ol>
 * 每一步都会通过 AskProgress 通知进度，前端据此实时显示。
 */
@Service
public class AskService {

    private static final Logger log = LoggerFactory.getLogger(AskService.class);

    private final AnswerCache answerCache;
    private final GlossaryService glossary;
    private final SchemaProvider schemaProvider;
    private final ExampleStore exampleStore;
    private final SqlGenerator sqlGenerator;
    private final SqlExecutor sqlExecutor;
    private final QueryLogService queryLog;
    private final int maxCorrections;

    public AskService(AnswerCache answerCache, GlossaryService glossary, SchemaProvider schemaProvider,
                      ExampleStore exampleStore, SqlGenerator sqlGenerator, SqlExecutor sqlExecutor,
                      QueryLogService queryLog, AskDbProperties properties) {
        this.answerCache = answerCache;
        this.glossary = glossary;
        this.schemaProvider = schemaProvider;
        this.exampleStore = exampleStore;
        this.sqlGenerator = sqlGenerator;
        this.sqlExecutor = sqlExecutor;
        this.queryLog = queryLog;
        this.maxCorrections = Math.max(0, properties.correction().maxAttempts());
    }

    public AskResult ask(String question) {
        return ask(question, AskProgress.NONE);
    }

    public AskResult ask(String question, AskProgress progress) {
        long start = System.currentTimeMillis();
        Optional<AskResult> cached = answerCache.get(question);
        AskResult result;
        if (cached.isPresent()) {
            progress.step(new Step("CACHE", "这个问题之前问过，直接返回上次的结果"));
            result = cached.get().asCacheHit(elapsed(start));
        } else {
            result = answer(question, progress, start);
            answerCache.put(result);
        }
        queryLog.record(result);
        return result;
    }

    /** 准备好的生成材料，以及对应的过程记录 */
    private record Prepared(GenerationRequest request, Trace trace) {
    }

    private AskResult answer(String question, AskProgress progress, long start) {
        Prepared prepared;
        GeneratedSql generated;
        try {
            prepared = prepare(question, progress);
            progress.step(new Step("GENERATE", "正在生成 SQL"));
            generated = sqlGenerator.generate(prepared.request());
        } catch (Exception e) {
            log.warn("生成 SQL 失败，问题：{}", question, e);
            return new AskResult(question, null, null, Status.GENERATION_FAILED, e.getMessage(),
                    null, null, false, elapsed(start));
        }
        return executeWithCorrection(question, prepared, generated, progress, start);
    }

    /** 匹配术语、选表、找示例，拼成交给模型的材料 */
    private Prepared prepare(String question, AskProgress progress) {
        List<Term> terms = glossary.match(question);
        if (!terms.isEmpty()) {
            progress.step(new Step("GLOSSARY", "用到业务术语：" + String.join("、", terms.stream().map(Term::term).toList())));
        }

        Set<String> requiredTables = new LinkedHashSet<>();
        terms.forEach(t -> requiredTables.addAll(t.tables()));
        SchemaContext schema = schemaProvider.describe(question, requiredTables);
        if (schema.totalTables() == 0) {
            // 库里一张表都没有时，调模型也只能瞎猜表名，自动修正也救不回来，直接说明原因
            throw new IllegalStateException("数据库里还没有任何表：请先导入数据（README 第 3 步），导入后重启后端。");
        }
        progress.step(new Step("SCHEMA", schema.tables().size() == schema.totalTables()
                ? "使用全部 " + schema.totalTables() + " 张表"
                : "从 " + schema.totalTables() + " 张表里选出 " + String.join("、", schema.tables())));

        List<Example> examples = exampleStore.findSimilar(question);
        if (!examples.isEmpty()) {
            progress.step(new Step("EXAMPLES", "参考 " + examples.size() + " 条已确认正确的相似问题"));
        }

        GenerationRequest request = new GenerationRequest(question, schema.text(), PromptSections.of(terms, examples));
        Trace trace = new Trace(schema.tables(), schema.totalTables(), terms.stream().map(Term::term).toList(),
                examples.size(), 0);
        return new Prepared(request, trace);
    }

    /**
     * 执行 SQL；报错（或者是能改写的写法问题）时，把报错交给模型修正后再执行，最多 maxCorrections 次。
     * 想修改数据这类被拦截的 SQL 不修正，直接返回。
     */
    private AskResult executeWithCorrection(String question, Prepared prepared, GeneratedSql generated,
                                            AskProgress progress, long start) {
        String sql = generated.sql();
        TokenUsage usage = generated.usage();
        int corrections = 0;

        while (true) {
            Trace trace = prepared.trace().withCorrections(corrections);
            progress.step(new Step("EXECUTE", corrections == 0 ? "正在执行查询" : "正在执行修正后的 SQL"));

            Status failedStatus;
            String error;
            try {
                QueryResult result = sqlExecutor.query(sql);
                return new AskResult(question, sql, result, Status.SUCCESS, null, usage, trace, false, elapsed(start));
            } catch (UnsafeSqlException e) {
                log.warn("拒绝执行：{}，原因：{}", sql, e.getMessage());
                if (!e.fixable()) {
                    return new AskResult(question, sql, null, Status.REJECTED, e.getMessage(), usage, trace, false, elapsed(start));
                }
                failedStatus = Status.REJECTED;
                error = e.getMessage();
            } catch (Exception e) {
                failedStatus = Status.EXECUTION_FAILED;
                error = rootMessage(e);
                log.info("SQL 执行失败：{}，原因：{}", sql, error);
            }

            if (corrections >= maxCorrections) {
                return new AskResult(question, sql, null, failedStatus, error, usage, trace, false, elapsed(start));
            }
            corrections++;
            progress.step(new Step("FIX", "SQL 有问题，正在根据报错修正（第 " + corrections + " 次）"));
            try {
                GeneratedSql fixed = sqlGenerator.fix(prepared.request(), sql, error);
                sql = fixed.sql();
                usage = usage.plus(fixed.usage());
            } catch (Exception e) {
                log.warn("修正 SQL 失败：{}", e.getMessage());
                return new AskResult(question, sql, null, failedStatus, error, usage, trace, false, elapsed(start));
            }
        }
    }

    private static long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }

    /** Spring 会把数据库报错包好几层，取最里面那层，信息最直接。 */
    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
