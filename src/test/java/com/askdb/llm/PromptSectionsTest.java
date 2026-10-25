package com.askdb.llm;

import com.askdb.examples.Example;
import com.askdb.glossary.Term;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptSectionsTest {

    @Test
    void 没有术语和示例时为空() {
        assertEquals("", PromptSections.of(List.of(), List.of()));
    }

    @Test
    void 拼出术语和示例两段() {
        String text = PromptSections.of(
                List.of(new Term("GMV", List.of("gmv"), "订单商品价格之和", List.of())),
                List.of(new Example("2017 年的 GMV", "SELECT SUM(price) FROM order_items\n")));
        assertTrue(text.contains("- GMV：订单商品价格之和"));
        assertTrue(text.contains("问题：2017 年的 GMV\nSQL：SELECT SUM(price) FROM order_items"));
    }
}
