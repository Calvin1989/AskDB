package com.askdb.agent;

import com.askdb.config.AskDbProperties;
import com.askdb.core.AskProgress;
import com.askdb.llm.GeneratedSql;
import com.askdb.llm.GeneratedSql.TokenUsage;
import com.askdb.llm.GenerationRequest;
import com.askdb.llm.LlmReplies;
import com.askdb.llm.LlmSettingsService;
import com.askdb.llm.SqlExtractor;
import com.askdb.schema.SchemaFormatter;
import com.askdb.schema.SchemaReader;
import com.askdb.schema.TableInfo;
import com.askdb.sql.SqlExecutor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent 模式：模型写 SQL 前可以自己调用工具查看数据（看其他表的结构、看某列的常见取值、试运行 SQL）。
 * <p>
 * 输入和普通模式一样（选好的表、术语、示例），区别是模型不用凭感觉猜"代金券在库里存的是什么"。
 * <p>
 * 工具调用的循环由这里控制，而不是交给 Spring AI 自动执行：这样能限制最多几轮，避免模型一直调用工具，
 * 也能把每一轮的 token 加起来。
 */
@Component
public class SqlAgent {

    /** 一轮里模型可能同时调用几个工具；超过这么多轮还没给出 SQL 就算失败 */
    private static final int MAX_ROUNDS = 8;
    private static final ToolCallingManager TOOL_CALLING = ToolCallingManager.builder().build();

    private final LlmSettingsService settingsService;
    private final SchemaReader schemaReader;
    private final SchemaFormatter formatter;
    private final SqlExecutor sqlExecutor;
    private final int maxToolCalls;
    private final String systemPrompt;
    private final String toolsPrompt;
    private final String userPrompt;

    public SqlAgent(LlmSettingsService settingsService, SchemaReader schemaReader, SchemaFormatter formatter,
                    SqlExecutor sqlExecutor, AskDbProperties properties) {
        this.settingsService = settingsService;
        this.schemaReader = schemaReader;
        this.formatter = formatter;
        this.sqlExecutor = sqlExecutor;
        this.maxToolCalls = properties.agent().maxToolCalls();
        this.systemPrompt = LlmReplies.loadPrompt("prompts/sql-system.st");
        this.toolsPrompt = LlmReplies.loadPrompt("prompts/agent-tools.st");
        this.userPrompt = LlmReplies.loadPrompt("prompts/sql-user.st");
    }

    /**
     * @param sql       最终的 SQL 和所有轮次加起来的 token
     * @param toolCalls 调用过的工具，按顺序
     */
    public record AgentAnswer(GeneratedSql sql, List<String> toolCalls) {
    }

    public AgentAnswer generate(GenerationRequest request, AskProgress progress) {
        AgentTools tools = new AgentTools(schemaReader, formatter, sqlExecutor, progress, maxToolCalls);
        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                .toolCallbacks(ToolCallbacks.from(tools))
                .internalToolExecutionEnabled(false)
                .build();
        String user = new PromptTemplate(userPrompt).render(Map.of(
                "schema", request.schema(), "extra", request.extra(), "question", request.question()));
        Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt()), new UserMessage(user)), options);

        ChatClient client = settingsService.chatClient();
        ChatResponse response = client.prompt(prompt).call().chatResponse();
        TokenUsage usage = LlmReplies.usage(response);
        for (int round = 1; response != null && response.hasToolCalls(); round++) {
            if (round > MAX_ROUNDS) {
                throw new IllegalStateException("模型调用工具 " + MAX_ROUNDS + " 轮后仍没有给出 SQL");
            }
            ToolExecutionResult executed = TOOL_CALLING.executeToolCalls(prompt, response);
            prompt = new Prompt(executed.conversationHistory(), options);
            response = client.prompt(prompt).call().chatResponse();
            usage = usage.plus(LlmReplies.usage(response));
        }
        String sql = SqlExtractor.extract(LlmReplies.text(response));
        return new AgentAnswer(new GeneratedSql(sql, usage), tools.calls());
    }

    /** 生成 SQL 的规则 + 工具用法 + 库里全部表的名字（模型只看到了选中的表，要知道还有哪些表可以查） */
    private String systemPrompt() {
        String allTables = schemaReader.readAll().stream()
                .map(SqlAgent::tableLine)
                .collect(Collectors.joining("\n"));
        return systemPrompt + "\n\n" + toolsPrompt
                .replace("{maxToolCalls}", String.valueOf(maxToolCalls))
                .replace("{allTables}", allTables);
    }

    private static String tableLine(TableInfo table) {
        return table.comment() == null || table.comment().isBlank()
                ? "- " + table.name()
                : "- " + table.name() + "：" + table.comment().strip();
    }
}
