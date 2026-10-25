package com.askdb.sql;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;

import java.util.List;
import java.util.Set;

/**
 * 执行前检查：只放行"单条 SELECT 语句"，并且只能查当前数据库里的表。
 * <p>
 * 这是三层防护里的第一层：
 * <ol>
 *   <li>这里：解析 SQL，不是单条查询就拒绝；表名前带了库名（如 askdb_meta.feedback、information_schema.tables）也拒绝，
 *       避免模型生成的 SQL 去读日志库或系统表</li>
 *   <li>MySQL 驱动默认不允许一次执行多条语句（allowMultiQueries=false）</li>
 *   <li>数据库账号本身只有查询权限（db/setup-reader.sql），就算前两层漏了也改不了数据</li>
 * </ol>
 * 注意：要用 parseStatements 而不是 parse。parse 只解析第一条语句，
 * "SELECT 1; DROP TABLE orders" 会被当成合法的 SELECT。
 */
public final class SqlGuard {

    private static final int MAX_ERROR_LENGTH = 200;

    private SqlGuard() {
    }

    public static void check(String sql) {
        List<Statement> statements = parse(sql);
        if (statements.size() != 1) {
            throw new UnsafeSqlException("一次只能执行一条 SQL，收到了 " + statements.size() + " 条", false);
        }
        Statement statement = statements.get(0);
        if (!(statement instanceof Select)) {
            throw new UnsafeSqlException("只允许执行查询语句（SELECT），收到的是 "
                    + statement.getClass().getSimpleName(), false);
        }
        checkTables(statement);
    }

    /** 表名里带"."说明指定了库名，只允许查当前库 */
    private static void checkTables(Statement statement) {
        Set<String> tables;
        try {
            tables = new TablesNamesFinder<>().getTables(statement);
        } catch (RuntimeException e) {
            throw new UnsafeSqlException("无法检查这条 SQL 用到了哪些表", true);
        }
        for (String table : tables) {
            if (table.replace("`", "").contains(".")) {
                throw new UnsafeSqlException("只能查询当前数据库里的表，表名前不要带库名：" + table, true);
            }
        }
    }

    private static List<Statement> parse(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new UnsafeSqlException("SQL 为空", true);
        }
        try {
            Statements statements = CCJSqlParserUtil.parseStatements(sql);
            return statements == null ? List.of() : statements.getStatements();
        } catch (JSQLParserException e) {
            throw new UnsafeSqlException("无法解析这条 SQL：" + firstLine(e.getMessage()), true);
        }
    }

    /** 解析器的报错会列出几十个"期望的符号"，只保留第一行。 */
    private static String firstLine(String message) {
        if (message == null) {
            return "未知错误";
        }
        String line = message.lines().findFirst().orElse(message);
        return line.length() > MAX_ERROR_LENGTH ? line.substring(0, MAX_ERROR_LENGTH) + "…" : line;
    }
}
