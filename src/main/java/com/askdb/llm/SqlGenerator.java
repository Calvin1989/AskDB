package com.askdb.llm;

import com.askdb.llm.GeneratedSql.TokenUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
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
        this.systemPrompt = loadPrompt("prompts/sql-system.st");
        this.userPrompt = loadPrompt("prompts/sql-user.st");
        this.fixPrompt = loadPrompt("prompts/sql-fix.st");
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

        if (response == null || response.getResult() == null) {
            throw new IllegalStateException("模型没有返回结果");
        }
        String reply = response.getResult().getOutput().getText();
        return new GeneratedSql(SqlExtractor.extract(reply), tokenUsage(response));
    }

    /** 有的服务商不返回用量，这时记 0。 */
    private static TokenUsage tokenUsage(ChatResponse response) {
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        if (usage == null) {
            return new TokenUsage(0, 0);
        }
        return new TokenUsage(orZero(usage.getPromptTokens()), orZero(usage.getCompletionTokens()));
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    /** 明确按 UTF-8 读取：Windows 上 JDK 17 默认编码是 GBK，不指定会乱码。 */
    private static String loadPrompt(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("读取提示词失败：" + path, e);
        }
    }
}
