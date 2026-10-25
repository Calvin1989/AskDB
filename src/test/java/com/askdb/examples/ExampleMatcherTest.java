package com.askdb.examples;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExampleMatcherTest {

    private static final List<Example> EXAMPLES = List.of(
            new Example("2017 年的 GMV 是多少？", "SELECT 1"),
            new Example("每种支付方式各有多少笔？", "SELECT 2"),
            new Example("2018 年的 GMV 是多少", "SELECT 3"));

    @Test
    void 找到相似的问题() {
        List<Example> found = ExampleMatcher.findSimilar(EXAMPLES, "2016 年的 GMV 是多少？", 3, 0.3);
        assertEquals(2, found.size());
        assertTrue(found.stream().allMatch(e -> e.question().contains("GMV")));
    }

    @Test
    void 完全相同的问题不当示例() {
        List<Example> found = ExampleMatcher.findSimilar(EXAMPLES, " 2018 年的 GMV 是多少？", 3, 0.3);
        assertTrue(found.stream().noneMatch(e -> e.sql().equals("SELECT 3")));
    }

    @Test
    void 不相似的不返回() {
        assertTrue(ExampleMatcher.findSimilar(EXAMPLES, "哪个州的卖家最多", 3, 0.3).isEmpty());
    }
}
