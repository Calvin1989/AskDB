package com.askdb.llm;

/**
 * 模型生成的 SQL，以及这次调用用了多少 token。
 *
 * @param sql   取出来的 SQL
 * @param usage token 用量
 */
public record GeneratedSql(String sql, TokenUsage usage) {

    /**
     * @param promptTokens     输入 token 数（提示词 + 表结构 + 问题）
     * @param completionTokens 输出 token 数（模型的回复）
     */
    public record TokenUsage(int promptTokens, int completionTokens) {

        /** 生成 + 修正多次调用模型时，把用量加起来 */
        public TokenUsage plus(TokenUsage other) {
            return new TokenUsage(promptTokens + other.promptTokens, completionTokens + other.completionTokens);
        }
    }
}
