package com.askdb.agent;

import com.askdb.core.AskProgress;
import com.askdb.core.AskProgress.Step;
import com.askdb.schema.SchemaFormatter;
import com.askdb.schema.SchemaReader;
import com.askdb.schema.TableInfo;
import com.askdb.sql.QueryResult;
import com.askdb.sql.SqlExecutor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 交给模型调用的工具：看表结构、看某列的常见取值、试运行 SQL。每次提问新建一个，记录这次调用了哪些工具。
 * <p>
 * 安全：表名、列名必须是库里真实存在的（按白名单检查后才拼进 SQL）；所有查询都走 SqlExecutor，
 * 和正式查询一样经过 SqlGuard 检查，并且用只读账号执行。
 */
public class AgentTools {

    static final int MAX_VALUES = 20;
    static final int MAX_PREVIEW_ROWS = 5;
    private static final int MAX_VALUE_LENGTH = 60;

    private final SchemaReader schemaReader;
    private final SchemaFormatter formatter;
    private final SqlExecutor sqlExecutor;
    private final AskProgress progress;
    private final int maxCalls;
    private final List<String> calls = new ArrayList<>();

    public AgentTools(SchemaReader schemaReader, SchemaFormatter formatter, SqlExecutor sqlExecutor,
                      AskProgress progress, int maxCalls) {
        this.schemaReader = schemaReader;
        this.formatter = formatter;
        this.sqlExecutor = sqlExecutor;
        this.progress = progress;
        this.maxCalls = maxCalls;
    }

    /** 这次提问调用过的工具，按顺序，给页面和评测报告看 */
    public List<String> calls() {
        return List.copyOf(calls);
    }

    @Tool(name = "describe_tables", description = "查看指定表的结构（字段、类型、注释）。已给出的表不够用时调用。")
    public String describeTables(@ToolParam(description = "表名，可以一次查多张") List<String> tables) {
        if (budgetUsedUp()) {
            return BUDGET_USED_UP;
        }
        Map<String, TableInfo> all = tablesByName();
        if (tables == null) {
            tables = List.of();
        }
        List<TableInfo> found = tables.stream().map(t -> all.get(t.strip().toLowerCase())).filter(t -> t != null).toList();
        record("查看表结构：" + String.join("、", tables));
        if (found.isEmpty()) {
            return "没有这些表。库里的表有：" + String.join("、", all.keySet());
        }
        return formatter.format(found);
    }

    @Tool(name = "column_values", description = "查看某一列最常见的取值（最多 20 个）和出现次数。"
            + "不确定状态、类型、地区代码、类别名等在库里具体怎么存时调用，不要凭感觉猜。")
    public String columnValues(@ToolParam(description = "表名") String table,
                               @ToolParam(description = "列名") String column) {
        if (budgetUsedUp()) {
            return BUDGET_USED_UP;
        }
        record("查看 " + table + "." + column + " 的常见取值");
        Optional<String> sql = columnValuesSql(tablesByName(), table, column);
        if (sql.isEmpty()) {
            return "没有这个列：" + table + "." + column + "。请先用 describe_tables 确认表名和列名。";
        }
        try {
            QueryResult result = sqlExecutor.query(sql.get());
            return result.rows().stream()
                    .map(row -> shorten(row.get(0)) + "（" + row.get(1) + " 次）")
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "查询失败：" + e.getMessage();
        }
    }

    @Tool(name = "run_sql", description = "试运行一条 SELECT 语句，返回结果行数和前 5 行，或者报错信息。"
            + "写好 SQL 后用来检查结果是否合理（是不是空的、数量级对不对）。")
    public String runSql(@ToolParam(description = "要试运行的 SELECT 语句") String sql) {
        if (budgetUsedUp()) {
            return BUDGET_USED_UP;
        }
        try {
            QueryResult result = sqlExecutor.query(sql);
            record("试运行 SQL，返回 " + result.rows().size() + " 行");
            return preview(result);
        } catch (Exception e) {
            record("试运行 SQL，报错");
            return "执行失败：" + rootMessage(e);
        }
    }

    private static final String BUDGET_USED_UP = "工具调用次数已用完，请根据已有信息直接给出最终的 SQL。";

    private boolean budgetUsedUp() {
        return calls.size() >= maxCalls;
    }

    private void record(String description) {
        calls.add(description);
        progress.step(new Step("TOOL", description));
    }

    private Map<String, TableInfo> tablesByName() {
        return schemaReader.readAll().stream()
                .collect(Collectors.toMap(t -> t.name().toLowerCase(), Function.identity(), (a, b) -> a, java.util.LinkedHashMap::new));
    }

    /** 表名、列名都在白名单里才拼 SQL，避免模型传入的名字被当成 SQL 执行 */
    static Optional<String> columnValuesSql(Map<String, TableInfo> tables, String table, String column) {
        if (table == null || column == null) {
            return Optional.empty();
        }
        TableInfo info = tables.get(table.strip().toLowerCase());
        if (info == null) {
            return Optional.empty();
        }
        return info.columns().stream()
                .filter(c -> c.name().equalsIgnoreCase(column.strip()))
                .findFirst()
                .map(c -> "SELECT `" + c.name() + "` AS value, COUNT(*) AS cnt FROM `" + info.name() + "`"
                        + " GROUP BY `" + c.name() + "` ORDER BY cnt DESC LIMIT " + MAX_VALUES);
    }

    static String preview(QueryResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(result.rows().size()).append(result.truncated() ? " 行以上" : " 行");
        if (result.rows().isEmpty()) {
            return sb.append("（结果为空）").toString();
        }
        sb.append("，前 ").append(Math.min(MAX_PREVIEW_ROWS, result.rows().size())).append(" 行：\n");
        sb.append(String.join(" | ", result.columns()));
        for (List<Object> row : result.rows().subList(0, Math.min(MAX_PREVIEW_ROWS, result.rows().size()))) {
            sb.append('\n').append(row.stream().map(AgentTools::shorten).collect(Collectors.joining(" | ")));
        }
        return sb.toString();
    }

    private static String shorten(Object value) {
        String text = String.valueOf(value);
        return text.length() > MAX_VALUE_LENGTH ? text.substring(0, MAX_VALUE_LENGTH) + "…" : text;
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
