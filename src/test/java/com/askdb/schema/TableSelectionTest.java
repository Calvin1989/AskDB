package com.askdb.schema;

import com.askdb.schema.TableInfo.ColumnInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 用简化的 Olist 表结构检查选表和补齐关联 */
class TableSelectionTest {

    private static final List<TableInfo> TABLES = List.of(
            new TableInfo("customers", "客户表", List.of(
                    new ColumnInfo("customer_id", "VARCHAR(32)", true, "每个订单对应一个 customer_id"),
                    new ColumnInfo("customer_state", "CHAR(2)", false, "州代码，如 SP、RJ"))),
            new TableInfo("sellers", "卖家表", List.of(
                    new ColumnInfo("seller_id", "VARCHAR(32)", true, "卖家ID"),
                    new ColumnInfo("seller_state", "CHAR(2)", false, "州代码，如 SP、RJ"))),
            new TableInfo("orders", "订单表", List.of(
                    new ColumnInfo("order_id", "VARCHAR(32)", true, "订单ID"),
                    new ColumnInfo("customer_id", "VARCHAR(32)", false, "下单客户，关联 customers.customer_id"))),
            new TableInfo("order_items", "订单商品明细表（销售明细），每行是卖出的一件商品", List.of(
                    new ColumnInfo("order_id", "VARCHAR(32)", false, "关联 orders.order_id"),
                    new ColumnInfo("seller_id", "VARCHAR(32)", false, "关联 sellers.seller_id"),
                    new ColumnInfo("price", "DECIMAL(10,2)", false, "这件商品的售价"))),
            new TableInfo("order_payments", "订单支付表", List.of(
                    new ColumnInfo("order_id", "VARCHAR(32)", false, "关联 orders.order_id"),
                    new ColumnInfo("payment_value", "DECIMAL(10,2)", false, "这笔支付的金额"))));

    private final TableSelector selector = new TableSelector(TABLES);
    private final TableRelations relations = new TableRelations(TABLES);

    private List<String> select(String question, Set<String> required) {
        return relations.connect(selector.select(question, required, 4, 0.35));
    }

    @Test
    void 问题直接提到的表一定选上() {
        List<String> tables = select("SP 州的卖家一共卖出了多少件商品？", Set.of());
        assertTrue(tables.containsAll(List.of("sellers", "order_items")), tables.toString());
    }

    @Test
    void 自动补上关联需要的中间表() {
        List<String> tables = select("RJ 州的客户一共支付了多少钱？", Set.of());
        assertTrue(tables.containsAll(List.of("customers", "order_payments", "orders")), tables.toString());
    }

    @Test
    void 业务术语要求的表一定带上() {
        assertTrue(select("一共有多少卖家", Set.of("order_payments")).contains("order_payments"));
    }

    @Test
    void 什么都没匹配上时给全部表() {
        assertEquals(5, select("hello", Set.of()).size());
    }

    @Test
    void 从注释里解析关联关系() {
        assertEquals(Set.of("customers", "order_items", "order_payments"), relations.neighborsOf("orders"));
    }
}
