package com.askdb.llm;

import com.askdb.config.AskDbProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 按设置创建调用大模型的客户端。设置可以在页面上修改，所以不用 Spring AI 自动创建的那个固定客户端。
 * <p>
 * 手动创建时要把这几样接上，否则 application.yml 里的配置不生效：
 * <ul>
 *   <li>RestClient.Builder：带着 spring.http.client 的连接、读取超时</li>
 *   <li>RetryTemplate：spring.ai.retry 的重试次数和等待时间</li>
 *   <li>ResponseErrorHandler：决定哪些 HTTP 错误要重试（比如 429）</li>
 * </ul>
 */
@Component
public class LlmClientFactory {

    /** 测试连接时不重试，填错了能马上看到结果 */
    private static final RetryTemplate NO_RETRY = RetryTemplate.builder().maxAttempts(1).build();

    private final ObjectProvider<RestClient.Builder> restClientBuilder;
    private final ObjectProvider<WebClient.Builder> webClientBuilder;
    private final RetryTemplate retryTemplate;
    private final ResponseErrorHandler responseErrorHandler;
    private final double temperature;

    public LlmClientFactory(ObjectProvider<RestClient.Builder> restClientBuilder,
                            ObjectProvider<WebClient.Builder> webClientBuilder,
                            RetryTemplate retryTemplate,
                            ResponseErrorHandler responseErrorHandler,
                            AskDbProperties properties) {
        this.restClientBuilder = restClientBuilder;
        this.webClientBuilder = webClientBuilder;
        this.retryTemplate = retryTemplate;
        this.responseErrorHandler = responseErrorHandler;
        this.temperature = properties.llm().temperature();
    }

    public ChatClient create(LlmSettings settings) {
        return create(settings, retryTemplate);
    }

    public ChatClient createWithoutRetry(LlmSettings settings) {
        return create(settings, NO_RETRY);
    }

    private ChatClient create(LlmSettings settings, RetryTemplate retry) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(settings.baseUrl())
                .apiKey(settings.apiKey())
                .restClientBuilder(restClientBuilder.getIfAvailable(RestClient::builder))
                .webClientBuilder(webClientBuilder.getIfAvailable(WebClient::builder))
                .responseErrorHandler(responseErrorHandler)
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(settings.model())
                        .temperature(temperature)
                        .build())
                .retryTemplate(retry)
                .build();

        return ChatClient.create(chatModel);
    }
}
