package com.askdb.llm;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 调大模型生成 SQL，或者根据数据库报错修正 SQL，返回 SQL 和 token 用量。
 * 提示词放在 resources/prompts/ 下，改提示词不用改这里的代码。
 * 每次调用都从 LlmSettingsService 取当前客户端，页面上改了模型设置后立刻生效。
 */
@Component
public class SqlGenerator {

    private final LlmSettingsService settingsService;
    private final String systemPrompt;
    private final String userPrompt;
    private final String fixPrompt;

    public SqlGenerator(LlmSettingsService settingsService) {
        this.settingsService = settingsService;
        this.systemPrompt = LlmReplies.loadPrompt("prompts/sql-system.st");
        this.userPrompt = LlmReplies.loadPrompt("prompts/sql-user.st");
        this.fixPrompt = LlmReplies.loadPrompt("prompts/sql-fix.st");
    }

    public GeneratedSql generate(GenerationRequest request) {
        return call(userPrompt, request, Map.of());
    }

    /** 把上一次的 SQL 和数据库报错交给模型，让它修正 */
    public GeneratedSql fix(GenerationRequest request, String failedSql, String error) {
        return call(fixPrompt, request, Map.of("sql", failedSql, "error", error));
    }

    private GeneratedSql call(String template, GenerationRequest request, Map<String, Object> extraParams) {
        ChatResponse response = settingsService.chatClient().prompt()
                .system(systemPrompt)
                .user(u -> {
                    u.text(template)
                            .param("schema", request.schema())
                            .param("extra", request.extra())
                            .param("question", request.question());
                    extraParams.forEach(u::param);
                })
                .call()
                .chatResponse();
        return new GeneratedSql(SqlExtractor.extract(LlmReplies.text(response)), LlmReplies.usage(response));
    }
}
