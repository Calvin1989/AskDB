package com.askdb.agent;

import com.askdb.schema.TableInfo;
import com.askdb.schema.TableInfo.ColumnInfo;
import com.askdb.sql.QueryResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolsTest {

    private static final Map<String, TableInfo> TABLES = Map.of("order_payments", new TableInfo("order_payments", "支付表", List.of(
            new ColumnInfo("order_id", "VARCHAR(32)", false, "订单ID"),
            new ColumnInfo("payment_type", "VARCHAR(20)", false, "支付方式"))));

    @Test
    void 表名列名存在时生成查询常见取值的SQL() {
        assertEquals(Optional.of("SELECT `payment_type` AS value, COUNT(*) AS cnt FROM `order_payments`"
                        + " GROUP BY `payment_type` ORDER BY cnt DESC LIMIT 20"),
                AgentTools.columnValuesSql(TABLES, "order_payments", "payment_type"));
    }

    @Test
    void 名字大小写和空格不影响() {
        assertTrue(AgentTools.columnValuesSql(TABLES, " Order_Payments ", "PAYMENT_TYPE").isPresent());
    }

    @Test
    void 不存在的表或列不生成SQL() {
        assertEquals(Optional.empty(), AgentTools.columnValuesSql(TABLES, "orders", "payment_type"));
        assertEquals(Optional.empty(), AgentTools.columnValuesSql(TABLES, "order_payments", "amount"));
        assertEquals(Optional.empty(), AgentTools.columnValuesSql(TABLES, null, null));
    }

    @Test
    void 模型传入的列名不会被拼进SQL() {
        assertEquals(Optional.empty(),
                AgentTools.columnValuesSql(TABLES, "order_payments", "payment_type`; DROP TABLE orders; --"));
    }

    @Test
    void 试运行结果只给前5行() {
        List<List<Object>> rows = List.of(row("a", 1), row("b", 2), row("c", 3), row("d", 4), row("e", 5), row("f", 6));
        String preview = AgentTools.preview(new QueryResult(List.of("type", "cnt"), rows, false));
        assertTrue(preview.startsWith("共 6 行，前 5 行：\ntype | cnt\na | 1"), preview);
        assertTrue(!preview.contains("f | 6"), preview);
    }

    @Test
    void 空结果明确说明() {
        assertEquals("共 0 行（结果为空）", AgentTools.preview(new QueryResult(List.of("x"), List.of(), false)));
    }

    private static List<Object> row(Object... values) {
        return Arrays.asList(values);
    }
}
