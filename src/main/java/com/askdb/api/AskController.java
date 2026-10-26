package com.askdb.api;

import com.askdb.core.AskProgress;
import com.askdb.core.AskResult;
import com.askdb.core.AskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api")
public class AskController {

    private static final Logger log = LoggerFactory.getLogger(AskController.class);
    /** 改写追问 + 生成 + 最多两次修正，每次调模型最多等 60 秒，留足余量 */
    private static final long STREAM_TIMEOUT_MS = 300_000;

    private final AskService askService;
    private final AsyncTaskExecutor executor;

    public AskController(AskService askService, @Qualifier("applicationTaskExecutor") AsyncTaskExecutor executor) {
        this.askService = askService;
        this.executor = executor;
    }

    /**
     * @param question 问题
     * @param history  同一对话里前面的问题（从早到晚），用来理解"那 SP 州呢？"这类追问；可以不传
     */
    public record AskRequest(
            @NotBlank @Size(max = 500) String question,
            @Size(max = 10) List<@NotBlank @Size(max = 500) String> history) {

        List<String> historyOrEmpty() {
            return history == null ? List.of() : history.stream().map(String::trim).toList();
        }
    }

    /**
     * 提问，一次性返回结果。SQL 生成失败或执行报错也返回 200，具体情况看返回里的 status 和 error。
     */
    @PostMapping("/ask")
    public AskResult ask(@Valid @RequestBody AskRequest request) {
        return askService.ask(request.question().trim(), request.historyOrEmpty(), AskProgress.NONE);
    }

    /**
     * 提问，用 SSE 边处理边推送进度。事件：
     * <ul>
     *   <li>step：每完成一步推一次，内容是 {stage, message}</li>
     *   <li>result：最后的结果，内容和 /api/ask 的返回一样</li>
     * </ul>
     * 处理放在线程池里，请求线程立刻返回，不会被模型调用占住。
     */
    @PostMapping(path = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@Valid @RequestBody AskRequest request) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        String question = request.question().trim();
        List<String> history = request.historyOrEmpty();
        executor.execute(() -> {
            try {
                AskResult result = askService.ask(question, history, step -> send(emitter, "step", step));
                send(emitter, "result", result);
                emitter.complete();
            } catch (Exception e) {
                log.error("流式提问失败", e);
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    /** 用户中途关掉页面时发送会失败，忽略即可，后台照常把这次提问处理完并记日志 */
    private static void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            log.debug("推送 {} 失败（客户端可能已断开）：{}", event, e.getMessage());
        }
    }
}
