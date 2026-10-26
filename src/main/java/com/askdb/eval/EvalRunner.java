package com.askdb.eval;

import com.askdb.config.AskDbProperties;
import com.askdb.core.AskProgress;
import com.askdb.core.AskResult;
import com.askdb.core.AskService;
import com.askdb.eval.EvalReport.CaseOutcome;
import com.askdb.eval.ResultComparator.Match;
import com.askdb.llm.LlmSettingsService;
import com.askdb.sql.QueryResult;
import com.askdb.sql.SqlExecutor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 跑测试题，输出准确率，并把明细存成 JSON。
 * 只在 eval 配置下启动：mvn spring-boot:run -Dspring-boot.run.profiles=eval
 * <ul>
 *   <li>题集：askdb.eval.cases，默认是调参集 cases.json；考核集是 test-cases.json</li>
 *   <li>次数：askdb.eval.repeat，模型输出不完全固定，跑多次取平均更可信</li>
 * </ul>
 * 结果文件名带上对照组（baseline）和 Agent 模式（agent）的标记，方便区分不同方案。
 * 直接调用 AskService，所以测的是完整流程；以后加了选表、自动修正等功能，评测代码不用改。
 */
@Component
@Profile("eval")
public class EvalRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final AskService askService;
    private final SqlExecutor sqlExecutor;
    private final LlmSettingsService llmSettings;
    private final AskDbProperties.Eval config;
    private final ObjectMapper objectMapper;
    private final String variant;

    public EvalRunner(AskService askService, SqlExecutor sqlExecutor, LlmSettingsService llmSettings,
                      AskDbProperties properties, ObjectMapper objectMapper, Environment environment) {
        // 按实际生效的设置命名：Agent 也可能是在 askdb-local.yml 里打开的，而不是通过 agent 配置
        List<String> labels = new ArrayList<>(Arrays.stream(environment.getActiveProfiles())
                .filter(p -> !p.equals("eval") && !p.equals("agent"))
                .toList());
        if (properties.agent().enabled()) {
            labels.add("agent");
        }
        this.variant = String.join("-", labels);
        this.askService = askService;
        this.sqlExecutor = sqlExecutor;
        this.llmSettings = llmSettings;
        this.config = properties.eval();
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        String setName = setName();
        List<EvalCase> cases = loadCases();
        int repeat = Math.max(1, config.repeat());
        log.info("开始评测：题集 {}{}，共 {} 道题，跑 {} 次", setName, variant.isEmpty() ? "" : "（" + variant + "）", cases.size(), repeat);

        List<EvalReport> reports = new ArrayList<>(repeat);
        for (int run = 1; run <= repeat; run++) {
            EvalReport report = runOnce(cases, run, repeat);
            Path file = save(report, setName, run);
            log.info("{}明细已保存：{}", report.summary(), file.toAbsolutePath());
            reports.add(report);
        }
        if (repeat > 1) {
            log.info(EvalReport.summarizeRuns(reports));
        }
    }

    private EvalReport runOnce(List<EvalCase> cases, int run, int repeat) {
        LocalDateTime startedAt = LocalDateTime.now();
        List<CaseOutcome> outcomes = new ArrayList<>(cases.size());
        for (EvalCase evalCase : cases) {
            CaseOutcome outcome = runOne(evalCase);
            outcomes.add(outcome);
            log.info("[{}/{}] {} {} {}", run, repeat, outcome.correct() ? "✓" : "✗", evalCase.id(), evalCase.question());
        }
        return EvalReport.of(startedAt.toString(), llmSettings.settings().model(), outcomes);
    }

    private CaseOutcome runOne(EvalCase c) {
        QueryResult gold;
        try {
            gold = sqlExecutor.query(c.goldSql());
        } catch (Exception e) {
            return new CaseOutcome(c.id(), c.category(), c.question(), null, c.goldSql(), null,
                    "GOLD_FAILED", Match.NONE.name(), "标准答案执行失败：" + e.getMessage(), 0, 0, 0, 0);
        }

        AskResult r = askService.ask(c.question(), c.historyOrEmpty(), AskProgress.NONE);
        Match match = r.success() ? ResultComparator.compare(gold, r.result()) : Match.NONE;
        int promptTokens = r.usage() == null ? 0 : r.usage().promptTokens();
        int corrections = r.trace() == null ? 0 : r.trace().corrections();
        int toolCalls = r.trace() == null || r.trace().toolCalls() == null ? 0 : r.trace().toolCalls().size();
        return new CaseOutcome(c.id(), c.category(), c.question(), r.rewrittenQuestion(), c.goldSql(), r.sql(),
                r.status().name(), match.name(), r.error(), promptTokens, corrections, toolCalls, r.elapsedMs());
    }

    private List<EvalCase> loadCases() throws IOException {
        try (InputStream in = config.cases().getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<List<EvalCase>>() {
            });
        }
    }

    /** 题集名取文件名去掉 .json，用在结果文件名里，方便区分调参集和考核集。 */
    private String setName() {
        String filename = config.cases().getFilename();
        if (filename == null) {
            return "cases";
        }
        return filename.endsWith(".json") ? filename.substring(0, filename.length() - 5) : filename;
    }

    private Path save(EvalReport report, String setName, int run) throws IOException {
        Path dir = Path.of(config.outputDir());
        Files.createDirectories(dir);
        String time = LocalDateTime.parse(report.startedAt()).format(FILE_TIME);
        String label = variant.isEmpty() ? setName : setName + "-" + variant;
        Path file = dir.resolve("eval-" + label + "-" + time + "-run" + run + ".json");
        objectMapper.writer(SerializationFeature.INDENT_OUTPUT).writeValue(file.toFile(), report);
        return file;
    }
}
