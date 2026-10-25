package com.askdb.llm;

/**
 * 生成 SQL 需要交给模型的全部内容。
 *
 * @param question 用户的问题
 * @param schema   选中的表结构
 * @param extra    补充信息：业务术语定义、相似问题的示例（由 {@link PromptSections} 拼好，可能为空）
 */
public record GenerationRequest(String question, String schema, String extra) {
}
