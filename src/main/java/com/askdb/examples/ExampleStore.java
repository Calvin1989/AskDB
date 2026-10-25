package com.askdb.examples;

import com.askdb.config.AskDbProperties;
import com.askdb.sql.SqlGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 示例库：用户在页面上标记"结果正确"的问题和 SQL。之后遇到相似的问题，把它们作为参考交给模型，
 * 系统用得越多越准。
 * <p>
 * 数据存在 askdb_meta.feedback（建表见 db/setup-reader.sql），"不对"的反馈也存，方便以后分析错因。
 * 同一个问题以最后一次反馈为准：先标对、后来又标不对，就不再当示例。
 * <p>
 * 示例数量不多，全部读进内存再算相似度；有新反馈时重新读取。
 */
@Service
public class ExampleStore {

    private static final Logger log = LoggerFactory.getLogger(ExampleStore.class);

    public enum Verdict { CORRECT, WRONG }

    private static final String INSERT_SQL =
            "INSERT INTO askdb_meta.feedback (question, generated_sql, verdict) VALUES (?, ?, ?)";
    /** 取最近 2000 条反馈 */
    private static final String SELECT_SQL =
            "SELECT question, generated_sql, verdict FROM askdb_meta.feedback ORDER BY id DESC LIMIT 2000";

    private final JdbcTemplate jdbcTemplate;
    private final AskDbProperties.Examples config;
    private volatile List<Example> cache;
    private volatile boolean warned;

    public ExampleStore(JdbcTemplate jdbcTemplate, AskDbProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.config = properties.examples();
    }

    /** 和问题相似的已确认示例；示例库关闭或读取失败时返回空 */
    public List<Example> findSimilar(String question) {
        if (!config.enabled()) {
            return List.of();
        }
        return ExampleMatcher.findSimilar(examples(), question, config.maxExamples(), config.minSimilarity());
    }

    /**
     * 记录一条反馈。SQL 也要过安全检查：示例会交给模型参考，不能让人借反馈塞进修改数据的语句。
     *
     * @throws com.askdb.sql.UnsafeSqlException SQL 不是单条查询
     */
    public void record(String question, String sql, Verdict verdict) {
        SqlGuard.check(sql);
        jdbcTemplate.update(INSERT_SQL, question, sql, verdict.name());
        cache = null;
    }

    private List<Example> examples() {
        List<Example> current = cache;
        if (current == null) {
            current = load();
        }
        return current;
    }

    /** 读取成功才缓存；失败时（比如还没建表）下次再试，警告只打一次 */
    private List<Example> load() {
        try {
            List<String[]> rows = jdbcTemplate.query(SELECT_SQL, (rs, i) ->
                    new String[]{rs.getString(1), rs.getString(2), rs.getString(3)});
            // 查询是倒序的（最新在前），反过来逐条覆盖，留下每个问题最后一次的反馈
            Map<String, String[]> latest = new LinkedHashMap<>();
            for (int i = rows.size() - 1; i >= 0; i--) {
                latest.put(ExampleMatcher.normalize(rows.get(i)[0]), rows.get(i));
            }
            List<Example> examples = new ArrayList<>();
            for (String[] row : latest.values()) {
                if (Verdict.CORRECT.name().equals(row[2])) {
                    examples.add(new Example(row[0], row[1]));
                }
            }
            List<Example> loaded = List.copyOf(examples);
            cache = loaded;
            return loaded;
        } catch (Exception e) {
            if (!warned) {
                warned = true;
                log.warn("读取示例库失败（如果还没有执行新版 setup-reader.sql，请执行一次）：{}", e.getMessage());
            }
            return List.of();
        }
    }
}
