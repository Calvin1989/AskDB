package com.askdb.glossary;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlossaryMatchTest {

    private static final List<Term> TERMS = List.of(
            new Term("GMV", List.of("gmv", "成交额"), "订单商品价格之和", List.of("order_items")),
            new Term("差评率", List.of("差评"), "1 到 2 分算差评", List.of("order_reviews")));

    @Test
    void 关键词不区分大小写() {
        assertEquals(List.of("GMV"), GlossaryService.match(TERMS, "2018 年的 Gmv 是多少").stream().map(Term::term).toList());
    }

    @Test
    void 可以同时匹配多个术语() {
        assertEquals(2, GlossaryService.match(TERMS, "成交额最高的类别差评多吗").size());
    }

    @Test
    void 没有术语时返回空() {
        assertEquals(0, GlossaryService.match(TERMS, "一共有多少个订单").size());
    }
}
