package com.askdb.llm;

import com.askdb.config.AskDbProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 管理当前使用的模型设置，可以在页面上修改。
 * <ul>
 *   <li>启动时：先取配置文件和环境变量里的值（askdb.llm.*）；如果页面上保存过，以保存的为准</li>
 *   <li>页面保存时：写入 askdb.llm.settings-file（默认项目根目录的 askdb-llm.json，不提交到 Git），并立刻换用新设置</li>
 *   <li>部署到公网时要把 askdb.llm.editable 设为 false，否则任何人都能改你的 Key</li>
 * </ul>
 */
@Service
public class LlmSettingsService {

    private static final Logger log = LoggerFactory.getLogger(LlmSettingsService.class);
    private static final int MAX_MESSAGE_LENGTH = 300;

    /** 当前设置和按它创建好的客户端放在一起，保证两者始终对应 */
    private record Current(LlmSettings settings, ChatClient client) {
    }

    public record TestResult(boolean ok, String message, long elapsedMs) {
    }

    private final LlmClientFactory factory;
    private final ObjectMapper objectMapper;
    private final Path settingsFile;
    private final boolean editable;
    private volatile Current current;

    public LlmSettingsService(LlmClientFactory factory, ObjectMapper objectMapper, AskDbProperties properties) {
        this.factory = factory;
        this.objectMapper = objectMapper;
        AskDbProperties.Llm config = properties.llm();
        this.settingsFile = Path.of(config.settingsFile());
        this.editable = config.editable();

        LlmSettings defaults = new LlmSettings(config.baseUrl(), config.apiKey(), config.model()).normalized();
        LlmSettings saved = loadSaved();
        this.current = build(saved != null ? saved : defaults);
        log.info("模型设置：{} / {}（{}）", current.settings().baseUrl(), current.settings().model(),
                current.settings().configured() ? "已配置 Key" : "未配置 Key");
    }

    public LlmSettings settings() {
        return current.settings();
    }

    public boolean editable() {
        return editable;
    }

    /** 提问时调用。还没配置 Key 时抛出异常，提示去页面上设置 */
    public ChatClient chatClient() {
        Current c = current;
        if (c.client() == null) {
            throw new LlmNotConfiguredException();
        }
        return c.client();
    }

    /**
     * 保存新设置并立即生效。apiKey 留空表示沿用当前的 Key（页面上不会回显完整 Key）。
     *
     * @throws IllegalArgumentException 设置格式不对，信息可直接显示给用户
     */
    public synchronized LlmSettings update(LlmSettings request) {
        LlmSettings merged = merge(request);
        merged.validate();
        save(merged);
        current = build(merged);
        log.info("模型设置已更新：{} / {}", merged.baseUrl(), merged.model());
        return merged;
    }

    /** 用给定设置发一个很短的请求，看能不能连通。不保存，也不重试 */
    public TestResult test(LlmSettings request) {
        LlmSettings merged = merge(request);
        merged.validate();
        long start = System.currentTimeMillis();
        try {
            String reply = factory.createWithoutRetry(merged).prompt().user("请只回复 OK").call().content();
            long elapsed = System.currentTimeMillis() - start;
            return new TestResult(true, "连接成功，模型回复：" + truncate(reply == null ? "" : reply.strip()), elapsed);
        } catch (Exception e) {
            return new TestResult(false, "连接失败：" + truncate(rootMessage(e)), System.currentTimeMillis() - start);
        }
    }

    private LlmSettings merge(LlmSettings request) {
        LlmSettings normalized = request.normalized();
        String apiKey = normalized.apiKey().isBlank() ? current.settings().apiKey() : normalized.apiKey();
        return new LlmSettings(normalized.baseUrl(), apiKey, normalized.model());
    }

    private Current build(LlmSettings settings) {
        return new Current(settings, settings.configured() ? factory.create(settings) : null);
    }

    private LlmSettings loadSaved() {
        if (!Files.isRegularFile(settingsFile)) {
            return null;
        }
        try {
            return objectMapper.readValue(settingsFile.toFile(), LlmSettings.class).normalized();
        } catch (IOException e) {
            log.warn("读取 {} 失败，改用配置文件里的设置：{}", settingsFile.toAbsolutePath(), e.getMessage());
            return null;
        }
    }

    /** 先写临时文件再改名，避免写到一半程序退出，留下损坏的文件 */
    private void save(LlmSettings settings) {
        try {
            Path absolute = settingsFile.toAbsolutePath();
            if (absolute.getParent() != null) {
                Files.createDirectories(absolute.getParent());
            }
            Path temp = absolute.resolveSibling(absolute.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), settings);
            try {
                Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("保存设置失败：" + e.getMessage(), e);
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    private static String truncate(String text) {
        return text.length() <= MAX_MESSAGE_LENGTH ? text : text.substring(0, MAX_MESSAGE_LENGTH) + "…";
    }
}
