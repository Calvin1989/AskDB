package com.askdb.core;

import com.askdb.config.AskDbProperties;
import com.askdb.llm.LlmSettings;
import com.askdb.llm.LlmSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * 相同问题直接返回上次的答案，省掉一次模型调用（几秒钟 + token 费用）。
 * <ul>
 *   <li>只缓存成功的结果；失败的下次重新生成</li>
 *   <li>Redis 出问题时只打警告、当作没命中，不影响正常查询</li>
 *   <li>"相同问题"指去掉首尾空格、末尾问号句号、大小写后完全一样</li>
 *   <li>换了模型后不会拿到旧模型的答案：缓存键里包含当前的接口地址和模型名</li>
 *   <li>多轮追问按改写后的完整问题缓存："那 SP 州呢？"和直接问"SP 州……"命中同一个答案</li>
 * </ul>
 * 改了提示词或换了模型后，旧答案可能不再合适：把 KEY_PREFIX 里的版本号加 1 即可让旧缓存全部失效。
 */
@Component
public class AnswerCache {

    private static final Logger log = LoggerFactory.getLogger(AnswerCache.class);
    private static final String KEY_PREFIX = "askdb:answer:v3:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final LlmSettingsService llmSettings;
    private final AskDbProperties.Cache config;

    public AnswerCache(StringRedisTemplate redis, ObjectMapper objectMapper, LlmSettingsService llmSettings,
                       AskDbProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.llmSettings = llmSettings;
        this.config = properties.cache();
    }

    public Optional<AskResult> get(String question) {
        if (!config.enabled()) {
            return Optional.empty();
        }
        try {
            String json = redis.opsForValue().get(key(scope(), question));
            return json == null ? Optional.empty() : Optional.of(objectMapper.readValue(json, AskResult.class));
        } catch (Exception e) {
            log.warn("读取缓存失败，改为直接查询：{}", e.getMessage());
            return Optional.empty();
        }
    }

    public void put(AskResult result) {
        if (!config.enabled() || !result.success()) {
            return;
        }
        try {
            redis.opsForValue().set(key(scope(), result.effectiveQuestion()), objectMapper.writeValueAsString(result), config.ttl());
        } catch (Exception e) {
            log.warn("写入缓存失败：{}", e.getMessage());
        }
    }

    /** 不同模型的答案分开缓存 */
    private String scope() {
        LlmSettings s = llmSettings.settings();
        return s.baseUrl() + "|" + s.model();
    }

    static String key(String scope, String question) {
        return KEY_PREFIX + sha256(scope + "\n" + normalize(question));
    }

    static String normalize(String question) {
        return question.strip()
                .replaceAll("\\s+", " ")
                .replaceAll("[？?。.！!\\s]+$", "")
                .toLowerCase(Locale.ROOT);
    }

    private static String sha256(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
