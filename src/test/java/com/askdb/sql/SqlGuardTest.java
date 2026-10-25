package com.askdb.sql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlGuardTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT COUNT(*) FROM orders",
            "SELECT o.order_status, COUNT(*) FROM orders o GROUP BY o.order_status ORDER BY 2 DESC LIMIT 5",
            "WITH t AS (SELECT customer_id FROM orders) SELECT COUNT(*) FROM t",
            "SELECT seller_state FROM sellers UNION SELECT customer_state FROM customers",
            "SELECT DATE(order_purchase_timestamp) FROM orders WHERE order_purchase_timestamp >= '2018-01-01'",
            "SELECT AVG(DATEDIFF(order_delivered_customer_date, order_purchase_timestamp)) FROM orders"
    })
    void 放行查询语句(String sql) {
        assertDoesNotThrow(() -> SqlGuard.check(sql));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DELETE FROM orders",
            "UPDATE orders SET order_status = 'canceled'",
            "INSERT INTO sellers VALUES ('x', '1', 'a', 'SP')",
            "DROP TABLE orders",
            "TRUNCATE TABLE orders",
            "CREATE TABLE t (id INT)"
    })
    void 拒绝修改数据和表结构的语句(String sql) {
        assertThrows(UnsafeSqlException.class, () -> SqlGuard.check(sql));
    }

    @Test
    void 拒绝在查询后面拼接的第二条语句() {
        UnsafeSqlException e = assertThrows(UnsafeSqlException.class,
                () -> SqlGuard.check("SELECT 1; DROP TABLE orders"));
        assertTrue(e.getMessage().contains("一条"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM askdb_meta.feedback",
            "SELECT table_name FROM information_schema.tables",
            "SELECT o.order_id FROM orders o JOIN `mysql`.`user` u ON 1 = 1"
    })
    void 拒绝查询其他库的表(String sql) {
        UnsafeSqlException e = assertThrows(UnsafeSqlException.class, () -> SqlGuard.check(sql));
        assertTrue(e.fixable());
    }

    @Test
    void 修改数据的语句不允许修正() {
        assertFalse(assertThrows(UnsafeSqlException.class, () -> SqlGuard.check("DELETE FROM orders")).fixable());
    }

    @Test
    void 拒绝解析不了的内容() {
        assertThrows(UnsafeSqlException.class, () -> SqlGuard.check("这不是 SQL"));
        assertThrows(UnsafeSqlException.class, () -> SqlGuard.check("  "));
    }
}
