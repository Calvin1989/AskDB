package com.askdb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

import java.time.Duration;

/**
 * 项目里所有可调参数都集中在这里，对应 application.yml 里的 askdb.* 配置。
 */
@ConfigurationProperties(prefix = "askdb")
public record AskDbProperties(
        @DefaultValue Llm llm,
        @DefaultValue Query query,
        @DefaultValue Schema schema,
        @DefaultValue Glossary glossary,
        @DefaultValue Correction correction,
        @DefaultValue Examples examples,
        @DefaultValue RateLimit rateLimit,
        @DefaultValue Cache cache,
        @DefaultValue QueryLog queryLog,
        @DefaultValue Eval eval,
        @DefaultValue DataImport dataImport) {

    /**
     * 大模型设置的初始值。页面上保存过设置后，以保存的为准（存在 settingsFile 里）。
     *
     * @param editable 是否允许在页面上修改。部署到公网时必须设为 false
     */
    public record Llm(
            @DefaultValue("https://api.deepseek.com") String baseUrl,
            @DefaultValue("") String apiKey,
            @DefaultValue("deepseek-chat") String model,
            @DefaultValue("0") double temperature,
            @DefaultValue("askdb-llm.json") String settingsFile,
            @DefaultValue("true") boolean editable) {
    }

    /** 执行 SQL 时的限制。 */
    public record Query(
            @DefaultValue("200") int maxRows,
            @DefaultValue("10") int timeoutSeconds) {
    }

    /**
     * 给模型看哪些表。
     *
     * @param strategy          retrieval = 按问题选表；full = 全部表（对照组）
     * @param maxTables         按分数最多选几张（不含补齐关联的中间表和术语要求的表）
     * @param relativeThreshold 分数至少是最高分的多少才入选
     */
    public record Schema(
            @DefaultValue("retrieval") String strategy,
            @DefaultValue("4") int maxTables,
            @DefaultValue("0.35") double relativeThreshold) {
    }

    /** 业务词典：问题里出现术语时，把口径定义交给模型。 */
    public record Glossary(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("classpath:glossary.json") Resource file) {
    }

    /** SQL 执行报错后，把报错交给模型修正，最多修正几次。0 表示不修正。 */
    public record Correction(
            @DefaultValue("2") int maxAttempts) {
    }

    /**
     * 示例库：用户标记为"结果正确"的问题和 SQL，遇到相似问题时作为参考交给模型。
     *
     * @param maxExamples   每次最多带几条
     * @param minSimilarity 问题相似度至少多少才算相似（0 到 1）
     */
    public record Examples(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("3") int maxExamples,
            @DefaultValue("0.3") double minSimilarity) {
    }

    /**
     * 提问接口限流：每个 IP 在 window 时间内最多 requests 次。
     *
     * @param store memory = 存在本机内存（单机够用）；redis = 存在 Redis（多台服务器共享计数）
     */
    public record RateLimit(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("memory") String store,
            @DefaultValue("10") int requests,
            @DefaultValue("60s") Duration window) {
    }

    /** 相同问题的答案缓存在 Redis 里多久。需要先启动 Redis，所以默认关闭。 */
    public record Cache(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("30m") Duration ttl) {
    }

    /** 是否把每次提问记到 askdb_meta.query_log。 */
    public record QueryLog(
            @DefaultValue("true") boolean enabled) {
    }

    /** 评测：题集文件、重复运行次数、结果输出目录。 */
    public record Eval(
            @DefaultValue("classpath:eval/cases.json") Resource cases,
            @DefaultValue("1") int repeat,
            @DefaultValue("eval-results") String outputDir) {
    }

    /** 导入 Olist CSV：CSV 所在目录和每批插入的行数。 */
    public record DataImport(
            String dataDir,
            @DefaultValue("2000") int batchSize) {
    }
}
