package com.askdb.eval;

/**
 * 一道测试题。
 *
 * @param id       题号，如 q001
 * @param category 分类：single_table / aggregation / join / business_term
 * @param question 问题
 * @param goldSql  标准答案 SQL
 */
public record EvalCase(String id, String category, String question, String goldSql) {
}
