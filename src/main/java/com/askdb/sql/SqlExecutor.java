package com.askdb.sql;

import com.askdb.config.AskDbProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.List;

/**
 * 执行 SQL 的唯一入口。问答和评测（包括执行标准答案）都走这里，
 * 所以安全检查只需要放在这一个地方。
 */
@Component
public class SqlExecutor {

    private final JdbcTemplate jdbcTemplate;
    private final int maxRows;

    public SqlExecutor(DataSource dataSource, AskDbProperties properties) {
        this.maxRows = properties.query().maxRows();
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        // 多取一行，用来判断结果有没有被截断
        this.jdbcTemplate.setMaxRows(maxRows + 1);
        this.jdbcTemplate.setQueryTimeout(properties.query().timeoutSeconds());
    }

    /**
     * @throws UnsafeSqlException 不是单条 SELECT，没有执行
     */
    public QueryResult query(String sql) {
        SqlGuard.check(sql);
        return jdbcTemplate.query(sql, rs -> {
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            List<String> columns = new ArrayList<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                columns.add(meta.getColumnLabel(i));
            }

            List<List<Object>> rows = new ArrayList<>();
            while (rs.next()) {
                List<Object> row = new ArrayList<>(columnCount);
                for (int i = 1; i <= columnCount; i++) {
                    row.add(rs.getObject(i));
                }
                rows.add(row);
            }

            boolean truncated = rows.size() > maxRows;
            if (truncated) {
                rows.remove(rows.size() - 1);
            }
            return new QueryResult(columns, rows, truncated);
        });
    }
}
