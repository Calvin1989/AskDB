package com.askdb.llm;

/** 还没有配置 API Key 就提问时抛出，提示用户去页面上设置。 */
public class LlmNotConfiguredException extends RuntimeException {

    public LlmNotConfiguredException() {
        super("还没有配置模型 API Key：点页面左下角的\"模型设置\"填写。");
    }
}
