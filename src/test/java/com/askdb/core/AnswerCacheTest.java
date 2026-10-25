package com.askdb.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AnswerCacheTest {

    private static final String M = "https://api.deepseek.com|deepseek-chat";

    @Test
    void 空格和末尾标点不同算同一个问题() {
        assertEquals(AnswerCache.key(M, "每种订单状态各有多少单？"), AnswerCache.key(M, "  每种订单状态各有多少单  "));
        assertEquals(AnswerCache.key(M, "最常用的支付方式是什么?"), AnswerCache.key(M, "最常用的支付方式是什么。"));
    }

    @Test
    void 英文大小写不同算同一个问题() {
        assertEquals(AnswerCache.key(M, "2018 年的 GMV 是多少"), AnswerCache.key(M, "2018 年的 gmv 是多少"));
    }

    @Test
    void 中间多个空格合并成一个() {
        assertEquals("2018 年的 gmv", AnswerCache.normalize("2018   年的\tGMV ？"));
    }

    @Test
    void 换了模型不会用到旧答案() {
        assertNotEquals(AnswerCache.key(M, "一共有多少个订单"),
                AnswerCache.key("https://dashscope.aliyuncs.com/compatible-mode|qwen-plus", "一共有多少个订单"));
    }

    @Test
    void 不同问题不会撞键() {
        assertNotEquals(AnswerCache.key(M, "SP 州有多少个客户"), AnswerCache.key(M, "RJ 州有多少个客户"));
    }
}
