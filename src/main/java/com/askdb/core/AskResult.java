package com.askdb.core;

import com.askdb.llm.GeneratedSql.TokenUsage;
import com.askdb.sql.QueryResult;

import java.util.List;

/**
 * 一次提问的完整结果。失败也算一种结果（记录在哪一步失败、原因是什么），
 * 这样评测、调用日志和自动修正都能拿到失败信息。
 *
 * @param question  用户的问题
 * @param sql       最终的 SQL（修正过的话是修正后的）；生成失败时为 null
 * @param result    查询结果；失败时为 null
 * @param status    成功，或者在哪一步失败
 * @param error     失败原因；成功时为 null
 * @param usage     调模型用的 token（生成 + 所有修正加起来）；没调到模型时为 null
 * @param trace     过程记录：用了哪些表、术语、示例，修正了几次；生成前就失败时为 null
 * @param cached    是否直接返回了缓存的答案
 * @param elapsedMs 总耗时（毫秒）
 */
public record AskResult(
        String question,
        String sql,
        QueryResult result,
        Status status,
        String error,
        TokenUsage usage,
        Trace trace,
        boolean cached,
        long elapsedMs) {

    public enum Status {
        SUCCESS,
        /** 调模型失败，或者回复里取不出 SQL */
        GENERATION_FAILED,
        /** 生成的不是单条 SELECT，或者查了其他库的表，没有执行 */
        REJECTED,
        /** SQL 执行报错（修正次数用完后仍然报错） */
        EXECUTION_FAILED
    }

    /**
     * @param tables      交给模型的表
     * @param totalTables 库里一共几张表
     * @param terms       用到的业务术语
     * @param examples    参考了几条相似问题的示例
     * @param corrections 根据报错修正了几次
     */
    public record Trace(List<String> tables, int totalTables, List<String> terms, int examples, int corrections) {

        public Trace withCorrections(int corrections) {
            return new Trace(tables, totalTables, terms, examples, corrections);
        }
    }

    public boolean success() {
        return status == Status.SUCCESS;
    }

    /** 从缓存取出的答案：没有调模型，所以 token 记为空，耗时换成这次的。 */
    public AskResult asCacheHit(long elapsedMs) {
        return new AskResult(question, sql, result, status, error, null, trace, true, elapsedMs);
    }
}
