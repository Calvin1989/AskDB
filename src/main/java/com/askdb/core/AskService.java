package com.askdb.core;

import com.askdb.agent.SqlAgent;
import com.askdb.agent.SqlAgent.AgentAnswer;
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
import com.askdb.llm.QuestionRewriter;
import com.askdb.llm.QuestionRewriter.Rewritten;
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
 *   <li>多轮追问时，结合前面的问题把当前问题改写完整（QuestionRewriter）</li>
 *   <li>查缓存（AnswerCache），命中直接返回</li>
 *   <li>匹配业务术语（GlossaryService）</li>
 *   <li>选表（SchemaProvider），术语涉及的表一定带上</li>
 *   <li>找相似问题的已确认示例（ExampleStore）</li>
 *   <li>生成 SQL（SqlGenerator；Agent 模式下是 SqlAgent，写之前可以自己查数据）</li>
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
    private final QuestionRewriter rewriter;
    private final SqlAgent sqlAgent;
    private final int maxCorrections;
    private final AskDbProperties.Followup followup;
    private final boolean agentEnabled;

    public AskService(AnswerCache answerCache, GlossaryService glossary, SchemaProvider schemaProvider,
                      ExampleStore exampleStore, SqlGenerator sqlGenerator, SqlExecutor sqlExecutor,
                      QueryLogService queryLog, QuestionRewriter rewriter, SqlAgent sqlAgent,
                      AskDbProperties properties) {
        this.answerCache = answerCache;
        this.rewriter = rewriter;
        this.sqlAgent = sqlAgent;
        this.agentEnabled = properties.agent().enabled();
        this.followup = properties.followup();
        this.glossary = glossary;
        this.schemaProvider = schemaProvider;
        this.exampleStore = exampleStore;
        this.sqlGenerator = sqlGenerator;
        this.sqlExecutor = sqlExecutor;
        this.queryLog = queryLog;
        this.maxCorrections = Math.max(0, properties.correction().maxAttempts());
    }

    public AskResult ask(String question) {
        return ask(question, List.of(), AskProgress.NONE);
    }

    /**
     * @param history 同一对话里前面的问题（从早到晚），用来理解追问；没有时传空列表
     */
    public AskResult ask(String question, List<String> history, AskProgress progress) {
        long start = System.currentTimeMillis();
        Rewritten rewritten = resolve(question, history, progress);
        String effective = rewritten.question();
        String rewrittenQuestion = effective.equals(question) ? null : effective;

        Optional<AskResult> cached = answerCache.get(effective);
        AskResult result;
        if (cached.isPresent()) {
            progress.step(new Step("CACHE", "这个问题之前问过，直接返回上次的结果"));
            result = cached.get().asCacheHit(rewritten.usage(), elapsed(start)).withQuestion(question, rewrittenQuestion);
        } else {
            result = answer(effective, rewritten.usage(), progress, start).withQuestion(question, rewrittenQuestion);
            answerCache.put(result);
        }
        queryLog.record(result);
        return result;
    }

    /**
     * 有上文时把问题改写完整。改写失败不影响提问，按原问题继续（只是追问可能理解不对）。
     * 没有改写时 usage 为 null。
     */
    private Rewritten resolve(String question, List<String> history, AskProgress progress) {
        if (!followup.enabled() || history.isEmpty()) {
            return new Rewritten(question, null);
        }
        List<String> recent = history.subList(Math.max(0, history.size() - followup.maxHistory()), history.size());
        try {
            Rewritten rewritten = rewriter.rewrite(question, recent);
            if (!rewritten.question().equals(question)) {
                progress.step(new Step("REWRITE", "结合上文，理解为：" + rewritten.question()));
            }
            return rewritten;
        } catch (Exception e) {
            log.warn("改写追问失败，按原问题继续：{}", e.getMessage());
            return new Rewritten(question, null);
        }
    }

    /** 准备好的生成材料，以及对应的过程记录 */
    private record Prepared(GenerationRequest request, Trace trace) {
    }

    /** @param usedTokens 生成 SQL 之前已经用掉的 token（改写追问），没有时为 null */
    private AskResult answer(String question, TokenUsage usedTokens, AskProgress progress, long start) {
        Prepared prepared;
        GeneratedSql generated;
        try {
            prepared = prepare(question, progress);
            if (agentEnabled) {
                progress.step(new Step("GENERATE", "正在生成 SQL，需要时会先查看数据"));
                AgentAnswer answer = sqlAgent.generate(prepared.request(), progress);
                generated = answer.sql();
                prepared = new Prepared(prepared.request(), prepared.trace().withToolCalls(answer.toolCalls()));
            } else {
                progress.step(new Step("GENERATE", "正在生成 SQL"));
                generated = sqlGenerator.generate(prepared.request());
            }
        } catch (Exception e) {
            log.warn("生成 SQL 失败，问题：{}", question, e);
            return new AskResult(question, null, null, Status.GENERATION_FAILED, e.getMessage(),
                    usedTokens, null, false, elapsed(start));
        }
        TokenUsage usage = usedTokens == null ? generated.usage() : usedTokens.plus(generated.usage());
        return executeWithCorrection(question, prepared, new GeneratedSql(generated.sql(), usage), progress, start);
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
            throw new IllegalStateException("数据库里还没有任何表：请先导入数据（docs/development.md 第 3 步；Docker 部署是 docker compose run --rm init），导入后重启后端。");
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
                examples.size(), 0, List.of());
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
