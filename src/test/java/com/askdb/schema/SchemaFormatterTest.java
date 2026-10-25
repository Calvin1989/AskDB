package com.askdb.schema;

import com.askdb.schema.TableInfo.ColumnInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchemaFormatterTest {

    private final SchemaFormatter formatter = new SchemaFormatter();

    @Test
    void 输出表名字段类型主键和注释() {
        TableInfo orders = new TableInfo("orders", "订单表", List.of(
                new ColumnInfo("order_id", "VARCHAR(32)", true, "订单ID"),
                new ColumnInfo("customer_id", "VARCHAR(32)", false, "关联 customers.customer_id")));
        TableInfo noComment = new TableInfo("t", null, List.of(
                new ColumnInfo("x", "INT", false, " ")));

        String expected = """
                orders -- 订单表
                  order_id VARCHAR(32) PRIMARY KEY -- 订单ID
                  customer_id VARCHAR(32) -- 关联 customers.customer_id

                t
                  x INT""";
        assertEquals(expected, formatter.format(List.of(orders, noComment)));
    }
}
