package com.askdb.llm;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从模型的回复里取出 SQL。
 * 优先取 ```sql 代码块；没有就取第一个 ``` 代码块；都没有就把整段回复当成 SQL。
 */
public final class SqlExtractor {

    private static final Pattern SQL_BLOCK = Pattern.compile("```sql\\s*(.*?)```", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    /** 允许 ```mysql 这类语言标记（标记后必须换行，避免把 ```SELECT 里的 SELECT 当成标记） */
    private static final Pattern ANY_BLOCK = Pattern.compile("```(?:[a-zA-Z]+\\n)?\\s*(.*?)```", Pattern.DOTALL);

    private SqlExtractor() {
    }

    public static String extract(String reply) {
        if (reply == null || reply.isBlank()) {
            throw new IllegalArgumentException("模型没有返回内容");
        }
        String sql = firstGroup(SQL_BLOCK, reply);
        if (sql == null) {
            sql = firstGroup(ANY_BLOCK, reply);
        }
        if (sql == null) {
            sql = reply;
        }
        sql = stripTrailingSemicolons(sql.trim());
        if (sql.isEmpty()) {
            throw new IllegalArgumentException("模型回复里没有找到 SQL");
        }
        return sql;
    }

    private static String firstGroup(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static String stripTrailingSemicolons(String sql) {
        int end = sql.length();
        while (end > 0 && (sql.charAt(end - 1) == ';' || Character.isWhitespace(sql.charAt(end - 1)))) {
            end--;
        }
        return sql.substring(0, end);
    }
}
