package com.askdb.text;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextTokensTest {

    @Test
    void 中文按相邻两个字切() {
        assertEquals(Set.of("支付", "付金", "金额"), TextTokens.of("支付金额"));
    }

    @Test
    void 英文转小写并拆开下划线() {
        Set<String> tokens = TextTokens.of("order_status GMV");
        assertTrue(tokens.containsAll(Set.of("order_status", "order", "status", "gmv")));
    }

    @Test
    void 中英文混合和标点() {
        Set<String> tokens = TextTokens.of("SP 州的卖家？");
        assertTrue(tokens.containsAll(Set.of("sp", "州的", "的卖", "卖家")));
    }

    @Test
    void 重合程度() {
        assertEquals(1.0, TextTokens.jaccard(TextTokens.of("卖家"), TextTokens.of("卖家")));
        assertEquals(0.0, TextTokens.jaccard(TextTokens.of("卖家"), TextTokens.of("客户")));
    }
}
