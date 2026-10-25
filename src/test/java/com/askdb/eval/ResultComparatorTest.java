package com.askdb.eval;

import com.askdb.eval.ResultComparator.Match;
import com.askdb.sql.QueryResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResultComparatorTest {

    private static QueryResult result(List<String> columns, List<?>... rows) {
        @SuppressWarnings("unchecked")
        List<List<Object>> rowList = Arrays.stream(rows).map(r -> (List<Object>) r).toList();
        return new QueryResult(columns, rowList, false);
    }

    private static Match compare(QueryResult gold, QueryResult pred) {
        return ResultComparator.compare(gold, pred);
    }

    @Test
    void 行顺序不同算一致() {
        QueryResult gold = result(List.of("s", "n"), List.of("SP", 10), List.of("RJ", 5));
        QueryResult pred = result(List.of("s", "n"), List.of("RJ", 5), List.of("SP", 10));
        assertEquals(Match.EXACT, compare(gold, pred));
    }

    @Test
    void 列名和列顺序不同算一致() {
        QueryResult gold = result(List.of("state", "cnt"), List.of("SP", 10));
        QueryResult pred = result(List.of("total", "seller_state"), List.of(10, "SP"));
        assertEquals(Match.EXACT, compare(gold, pred));
    }

    @Test
    void 数字类型和末尾的零不同算一致() {
        assertEquals(Match.EXACT, compare(
                result(List.of("avg"), List.of(new BigDecimal("4.08670000"))),
                result(List.of("avg"), List.of(4.0867))));
        assertEquals(Match.EXACT, compare(
                result(List.of("n"), List.of(99441L)),
                result(List.of("n"), List.of(new BigDecimal("99441.00")))));
    }

    @Test
    void 模型保留两位小数算一致() {
        QueryResult gold = result(List.of("avg"), List.of(new BigDecimal("4.0709")));
        assertEquals(Match.EXACT, compare(gold, result(List.of("avg"), List.of(new BigDecimal("4.07")))));
    }

    @Test
    void 模型取整到个位不算一致() {
        QueryResult gold = result(List.of("avg"), List.of(new BigDecimal("4.0709")));
        assertEquals(Match.NONE, compare(gold, result(List.of("avg"), List.of(4L))));
    }

    @Test
    void 小数位相同但数值不同不算一致() {
        QueryResult gold = result(List.of("rate"), List.of(new BigDecimal("0.0063")));
        assertEquals(Match.NONE, compare(gold, result(List.of("rate"), List.of(new BigDecimal("0.0071")))));
    }

    @Test
    void 多查了一列单独记录() {
        QueryResult gold = result(List.of("type"), List.of("credit_card"));
        QueryResult pred = result(List.of("type", "cnt"), List.of("credit_card", 76795));
        assertEquals(Match.EXTRA_COLUMNS, compare(gold, pred));
    }

    @Test
    void 少查了一列不算一致() {
        QueryResult gold = result(List.of("type", "cnt"), List.of("credit_card", 76795));
        QueryResult pred = result(List.of("type"), List.of("credit_card"));
        assertEquals(Match.NONE, compare(gold, pred));
    }

    @Test
    void 每列的值都对但行对不上不算一致() {
        // 两边每一列单独看都是 {A,B} 和 {1,2}，但 A 对应的数不一样
        QueryResult gold = result(List.of("k", "v"), List.of("A", 1), List.of("B", 2));
        QueryResult pred = result(List.of("k", "v"), List.of("A", 2), List.of("B", 1));
        assertEquals(Match.NONE, compare(gold, pred));
    }

    @Test
    void 行里的值重复时不会误判() {
        QueryResult gold = result(List.of("a", "b"), List.of(1, 5), List.of(5, 1));
        QueryResult pred = result(List.of("a", "b"), List.of(1, 5), List.of(1, 5));
        assertEquals(Match.NONE, compare(gold, pred));
    }

    @Test
    void 值不同或行数不同不算一致() {
        assertEquals(Match.NONE, compare(result(List.of("n"), List.of(10)), result(List.of("n"), List.of(11))));
        assertEquals(Match.NONE, compare(result(List.of("n"), List.of(10)), result(List.of("n"))));
    }

    @Test
    void 空值能正确比较() {
        QueryResult gold = result(List.of("x"), Arrays.asList((Object) null));
        QueryResult pred = result(List.of("x"), Arrays.asList((Object) null));
        assertEquals(Match.EXACT, compare(gold, pred));
    }
}
