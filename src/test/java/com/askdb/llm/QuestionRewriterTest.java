package com.askdb.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestionRewriterTest {

    private static final String ORIGINAL = "那 SP 州呢？";

    @Test
    void 正常回复原样使用() {
        assertEquals("SP 州的客户一共支付了多少钱？", QuestionRewriter.clean("SP 州的客户一共支付了多少钱？", ORIGINAL));
    }

    @Test
    void 去掉说明前缀和引号() {
        assertEquals("SP 州的客户一共支付了多少钱？",
                QuestionRewriter.clean("改写后的问题：“SP 州的客户一共支付了多少钱？”", ORIGINAL));
        assertEquals("SP 州的客户一共支付了多少钱？", QuestionRewriter.clean("\"SP 州的客户一共支付了多少钱？\"", ORIGINAL));
    }

    @Test
    void 只取第一行非空文字() {
        assertEquals("SP 州的客户一共支付了多少钱？",
                QuestionRewriter.clean("\n  SP 州的客户一共支付了多少钱？\n（补上了上文的指标）", ORIGINAL));
    }

    @Test
    void 回复为空时用原问题() {
        assertEquals(ORIGINAL, QuestionRewriter.clean("   \n ", ORIGINAL));
        assertEquals(ORIGINAL, QuestionRewriter.clean(null, ORIGINAL));
        assertEquals(ORIGINAL, QuestionRewriter.clean("“”", ORIGINAL));
    }

    @Test
    void 回复太长时用原问题() {
        assertEquals(ORIGINAL, QuestionRewriter.clean("问".repeat(501), ORIGINAL));
    }

    @Test
    void 前面的问题按顺序编号() {
        assertEquals("1. 2017 年 11 月一共有多少个订单？\n2. 12 月呢？\n",
                QuestionRewriter.formatHistory(List.of("2017 年 11 月一共有多少个订单？", " 12 月呢？ ")));
    }
}
