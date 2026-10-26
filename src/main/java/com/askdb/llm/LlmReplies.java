package com.askdb.llm;

import com.askdb.llm.GeneratedSql.TokenUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** 调模型时几个类共用的小工具（SqlGenerator、QuestionRewriter、SqlAgent）：读提示词文件、取回复文本、取 token 用量。 */
public final class LlmReplies {

    private LlmReplies() {
    }

    /** 明确按 UTF-8 读取：Windows 上 JDK 17 默认编码是 GBK，不指定会乱码。 */
    public static String loadPrompt(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("读取提示词失败：" + path, e);
        }
    }

    public static String text(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            throw new IllegalStateException("模型没有返回结果");
        }
        return response.getResult().getOutput().getText();
    }

    /** 有的服务商不返回用量，这时记 0。 */
    public static TokenUsage usage(ChatResponse response) {
        Usage usage = response == null || response.getMetadata() == null ? null : response.getMetadata().getUsage();
        if (usage == null) {
            return new TokenUsage(0, 0);
        }
        return new TokenUsage(orZero(usage.getPromptTokens()), orZero(usage.getCompletionTokens()));
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
