package com.askdb.eval;

import java.util.List;

/**
 * 一道测试题。
 *
 * @param id       题号，如 q001
 * @param category 分类：single_table / aggregation / join / business_term / followup
 * @param question 问题
 * @param goldSql  标准答案 SQL
 * @param history  追问题才有：同一对话里前面的问题，从早到晚
 */
public record EvalCase(String id, String category, String question, String goldSql, List<String> history) {

    public List<String> historyOrEmpty() {
        return history == null ? List.of() : history;
    }
}
