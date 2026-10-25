package com.askdb.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmSettingsTest {

    @Test
    void 去掉地址末尾多填的路径() {
        assertEquals("https://api.deepseek.com",
                new LlmSettings(" https://api.deepseek.com/v1/ ", "k", "m").normalized().baseUrl());
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode",
                new LlmSettings("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", "k", "m").normalized().baseUrl());
    }

    @Test
    void Key只露出头尾几位() {
        assertEquals("sk-****cdef", new LlmSettings("u", "sk-1234567890abcdef", "m").maskedApiKey());
        assertEquals("****", new LlmSettings("u", "short", "m").maskedApiKey());
        assertEquals("", new LlmSettings("u", "", "m").maskedApiKey());
    }

    @Test
    void 没有Key或只有占位符算未配置() {
        assertFalse(new LlmSettings("https://a.com", "", "m").configured());
        assertFalse(new LlmSettings("https://a.com", "not-set", "m").configured());
        assertTrue(new LlmSettings("https://a.com", "sk-x", "m").configured());
    }

    @Test
    void 格式不对时给出能看懂的提示() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new LlmSettings("api.deepseek.com", "sk-x", "m").validate());
        assertTrue(e.getMessage().contains("https://"));
        assertThrows(IllegalArgumentException.class, () -> new LlmSettings("https://a.com", "sk-x", " ").normalized().validate());
        assertThrows(IllegalArgumentException.class, () -> new LlmSettings("https://a.com", "", "m").validate());
    }

    @Test
    void 空值不会报空指针() {
        LlmSettings s = new LlmSettings(null, null, null).normalized();
        assertEquals("", s.baseUrl());
        assertFalse(s.configured());
    }
}
