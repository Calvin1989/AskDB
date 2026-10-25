package com.askdb.llm;

/**
 * 调用大模型需要的三项设置。DeepSeek、通义千问等都兼容 OpenAI 协议，只是地址和模型名不同。
 *
 * @param baseUrl 接口地址，不含 /v1/chat/completions，例如 https://api.deepseek.com
 * @param apiKey  API Key
 * @param model   模型名，例如 deepseek-chat
 */
public record LlmSettings(String baseUrl, String apiKey, String model) {

    private static final String PLACEHOLDER_KEY = "not-set";

    /** 去掉首尾空格和末尾多余的路径：程序会自动拼上 /v1/chat/completions，用户多填了会变成 /v1/v1/... */
    public LlmSettings normalized() {
        String url = trim(baseUrl);
        url = url.replaceAll("/+$", "");
        url = url.replaceAll("(?i)/chat/completions$", "");
        url = url.replaceAll("(?i)/v1$", "");
        return new LlmSettings(url, trim(apiKey), trim(model));
    }

    public boolean configured() {
        return !apiKey.isBlank() && !PLACEHOLDER_KEY.equals(apiKey) && !baseUrl.isBlank() && !model.isBlank();
    }

    /** 页面上显示的 Key：只露出头尾几位，完整的 Key 不会离开后端 */
    public String maskedApiKey() {
        if (apiKey.isBlank() || PLACEHOLDER_KEY.equals(apiKey)) {
            return "";
        }
        return apiKey.length() <= 8 ? "****" : apiKey.substring(0, 3) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    /** 检查格式，不合格时抛出带说明的异常，说明会直接显示在页面上 */
    public void validate() {
        if (!baseUrl.matches("(?i)^https?://[^\\s/]+.*$")) {
            throw new IllegalArgumentException("接口地址要以 http:// 或 https:// 开头，例如 https://api.deepseek.com");
        }
        if (model.isBlank()) {
            throw new IllegalArgumentException("请填写模型名称，例如 deepseek-chat");
        }
        if (apiKey.isBlank() || PLACEHOLDER_KEY.equals(apiKey)) {
            throw new IllegalArgumentException("请填写 API Key");
        }
    }

    private static String trim(String s) {
        return s == null ? "" : s.strip();
    }
}
