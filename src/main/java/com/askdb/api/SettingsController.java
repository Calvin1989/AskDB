package com.askdb.api;

import com.askdb.llm.LlmSettings;
import com.askdb.llm.LlmSettingsService;
import com.askdb.llm.LlmSettingsService.TestResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 模型设置接口，给前端的"模型设置"面板用。
 * 返回时只给出打码后的 Key，完整的 Key 不会离开后端。
 */
@RestController
@RequestMapping("/api/settings/llm")
public class SettingsController {

    private final LlmSettingsService settingsService;

    public SettingsController(LlmSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /** 提交的设置。apiKey 留空表示沿用当前的 Key */
    public record SettingsRequest(String baseUrl, String apiKey, String model) {

        LlmSettings toSettings() {
            return new LlmSettings(baseUrl, apiKey, model);
        }
    }

    public record SettingsView(String baseUrl, String model, String apiKeyMasked, boolean configured, boolean editable) {

        static SettingsView of(LlmSettings s, boolean editable) {
            return new SettingsView(s.baseUrl(), s.model(), s.maskedApiKey(), s.configured(), editable);
        }
    }

    @GetMapping
    public SettingsView get() {
        return SettingsView.of(settingsService.settings(), settingsService.editable());
    }

    @PutMapping
    public SettingsView update(@RequestBody SettingsRequest request) {
        checkEditable();
        try {
            return SettingsView.of(settingsService.update(request.toSettings()), true);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/test")
    public TestResult test(@RequestBody SettingsRequest request) {
        checkEditable();
        try {
            return settingsService.test(request.toSettings());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private void checkEditable() {
        if (!settingsService.editable()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "这个环境不允许在页面上修改模型设置（askdb.llm.editable=false），请修改服务端配置。");
        }
    }
}
