package com.askdb.llm;

import com.askdb.llm.GeneratedSql.TokenUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 多轮追问：把"那 SP 州呢？"这类依赖上文的问题，结合前面的问题改写成一个完整、独立的问题。
 * <p>
 * 改写后的问题再走正常流程（匹配术语、选表、找示例、生成 SQL），所以这些步骤都不用关心上下文。
 * 当前问题本身已经完整时，模型会原样返回。
 */
@Component
public class QuestionRewriter {

    /** 模型偶尔会在前面加"改写后的问题："之类的说明，去掉 */
    private static final Pattern LABEL = Pattern.compile("^(改写后的问题|改写后|完整的问题|问题)\\s*[：:]\\s*");
    private static final int MAX_LENGTH = 500;

    private final LlmSettingsService settingsService;
    private final String prompt;

    public QuestionRewriter(LlmSettingsService settingsService) {
        this.settingsService = settingsService;
        this.prompt = LlmReplies.loadPrompt("prompts/rewrite.st");
    }

    /**
     * @param question 当前问题
     * @param history  同一对话里前面的问题，从早到晚，不能为空
     */
    public Rewritten rewrite(String question, List<String> history) {
        ChatResponse response = settingsService.chatClient().prompt()
                .user(u -> u.text(prompt)
                        .param("history", formatHistory(history))
                        .param("question", question))
                .call()
                .chatResponse();
        return new Rewritten(clean(LlmReplies.text(response), question), LlmReplies.usage(response));
    }

    /**
     * @param question 改写后的问题（没变时和原问题一样）
     * @param usage    这次改写用的 token
     */
    public record Rewritten(String question, TokenUsage usage) {
    }

    static String formatHistory(List<String> history) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < history.size(); i++) {
            sb.append(i + 1).append(". ").append(history.get(i).strip()).append('\n');
        }
        return sb.toString();
    }

    /** 取回复的第一行非空文字，去掉说明前缀和引号；取不到合理内容时用原问题 */
    static String clean(String reply, String fallback) {
        if (reply == null) {
            return fallback;
        }
        String line = reply.lines().map(String::strip).filter(s -> !s.isEmpty()).findFirst().orElse("");
        line = LABEL.matcher(line).replaceFirst("");
        line = line.replaceAll("^[\"'“”「『]+|[\"'“”」』]+$", "").strip();
        return line.isEmpty() || line.length() > MAX_LENGTH ? fallback : line;
    }
}
