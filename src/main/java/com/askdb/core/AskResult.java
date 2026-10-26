package com.askdb.core;

import com.askdb.llm.GeneratedSql.TokenUsage;
import com.askdb.sql.QueryResult;

import java.util.List;

/**
 * 一次提问的完整结果。失败也算一种结果（记录在哪一步失败、原因是什么），
 * 这样评测、调用日志和自动修正都能拿到失败信息。
 *
 * @param question          用户的问题（原话）
 * @param rewrittenQuestion 多轮追问时，结合上文改写成的完整问题；没有改写时为 null
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
        String rewrittenQuestion,
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
     * @param toolCalls   Agent 模式下模型调用过的工具（如"查看 order_payments.payment_type 的常见取值"），普通模式为空
     */
    public record Trace(List<String> tables, int totalTables, List<String> terms, int examples, int corrections,
                        List<String> toolCalls) {

        public Trace withCorrections(int corrections) {
            return new Trace(tables, totalTables, terms, examples, corrections, toolCalls);
        }

        public Trace withToolCalls(List<String> toolCalls) {
            return new Trace(tables, totalTables, terms, examples, corrections, toolCalls);
        }
    }

    /** 没有改写过的结果 */
    public AskResult(String question, String sql, QueryResult result, Status status, String error,
                     TokenUsage usage, Trace trace, boolean cached, long elapsedMs) {
        this(question, null, sql, result, status, error, usage, trace, cached, elapsedMs);
    }

    public boolean success() {
        return status == Status.SUCCESS;
    }

    /** 实际拿去生成 SQL 的问题：改写过就是改写后的，否则是原话 */
    public String effectiveQuestion() {
        return rewrittenQuestion != null ? rewrittenQuestion : question;
    }

    /** 换成这次提问的原话和改写结果（缓存里的答案可能来自另一种问法） */
    public AskResult withQuestion(String question, String rewrittenQuestion) {
        return new AskResult(question, rewrittenQuestion, sql, result, status, error, usage, trace, cached, elapsedMs);
    }

    /** 从缓存取出的答案：没有生成 SQL，token 只算这次改写问题用的（没改写时为 null），耗时换成这次的。 */
    public AskResult asCacheHit(TokenUsage usage, long elapsedMs) {
        return new AskResult(question, rewrittenQuestion, sql, result, status, error, usage, trace, true, elapsedMs);
    }
}
