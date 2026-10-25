package com.askdb.core;

import com.askdb.config.AskDbProperties;
import com.askdb.llm.GeneratedSql.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 每次提问记一条日志到 askdb_meta.query_log（建表见 db/setup-reader.sql）。
 * 日志是辅助功能：写失败只打警告，不影响给用户返回结果。
 */
@Service
public class QueryLogService {

    private static final Logger log = LoggerFactory.getLogger(QueryLogService.class);

    private static final String INSERT_SQL = """
            INSERT INTO askdb_meta.query_log
                (question, generated_sql, status, error, row_count,
                 prompt_tokens, completion_tokens, cached, elapsed_ms)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    private static final int MAX_ERROR_LENGTH = 1000;

    private final JdbcTemplate jdbcTemplate;
    private final boolean enabled;

    public QueryLogService(JdbcTemplate jdbcTemplate, AskDbProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.enabled = properties.queryLog().enabled();
    }

    public void record(AskResult r) {
        if (!enabled) {
            return;
        }
        TokenUsage usage = r.usage();
        try {
            jdbcTemplate.update(INSERT_SQL,
                    r.question(),
                    r.sql(),
                    r.status().name(),
                    truncate(r.error()),
                    r.result() == null ? null : r.result().rows().size(),
                    usage == null ? null : usage.promptTokens(),
                    usage == null ? null : usage.completionTokens(),
                    r.cached(),
                    r.elapsedMs());
        } catch (Exception e) {
            log.warn("写调用日志失败：{}", e.getMessage());
        }
    }

    private static String truncate(String text) {
        return (text == null || text.length() <= MAX_ERROR_LENGTH) ? text : text.substring(0, MAX_ERROR_LENGTH);
    }
}
