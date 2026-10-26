package com.askdb;

import com.askdb.agent.AgentTools;
import com.askdb.core.AskProgress;
import com.askdb.schema.SchemaFormatter;
import com.askdb.schema.SchemaReader;
import com.askdb.schema.TableInfo;
import com.askdb.sql.QueryResult;
import com.askdb.sql.SqlExecutor;
import com.askdb.sql.UnsafeSqlException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 在真实的 MySQL 上验证 SQL 安全的三层防护、表结构读取和 Agent 工具。
 * 用 Testcontainers 自动启动 MySQL，本机没有 Docker 时整个类自动跳过；GitHub Actions 上每次推送都会跑。
 */
@Testcontainers(disabledWithoutDocker = true)
class MySqlIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0").withDatabaseName("askdb");

    private static final String READER_PASSWORD = "reader-pass-123";

    /** 只读账号，和正式运行时一样 */
    private static DataSource reader;
    private static SqlExecutor executor;

    @BeforeAll
    static void setUp() {
        DataSource admin = dataSource("root", MYSQL.getPassword());
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/olist-schema.sql"), new ClassPathResource("db/setup-reader.sql"));
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.execute(admin);

        JdbcTemplate root = new JdbcTemplate(admin);
        root.update("ALTER USER 'askdb_reader'@'%' IDENTIFIED BY ?", READER_PASSWORD);
        root.update("INSERT INTO orders (order_id, customer_id, order_status) VALUES "
                + "('o1', 'c1', 'delivered'), ('o2', 'c2', 'delivered'), ('o3', 'c1', 'canceled')");

        reader = dataSource("askdb_reader", READER_PASSWORD);
        executor = new SqlExecutor(reader, 200, 10);
    }

    @Test
    void 只读账号可以查询() {
        QueryResult result = executor.query("SELECT order_status, COUNT(*) AS cnt FROM orders GROUP BY order_status ORDER BY cnt DESC");
        assertEquals(List.of("order_status", "cnt"), result.columns());
        assertEquals("delivered", result.rows().get(0).get(0));
        assertEquals(2L, ((Number) result.rows().get(0).get(1)).longValue());
    }

    @Test
    void 第一层_代码检查拦下修改语句() {
        assertThrows(UnsafeSqlException.class, () -> executor.query("DELETE FROM orders"));
        assertThrows(UnsafeSqlException.class, () -> executor.query("SELECT * FROM askdb_meta.query_log"));
    }

    @Test
    void 第二层_驱动不允许一次执行多条语句() {
        JdbcTemplate jdbc = new JdbcTemplate(reader);
        assertThrows(DataAccessException.class, () -> jdbc.queryForList("SELECT 1; DELETE FROM orders"));
        assertEquals(3, countOrders());
    }

    @Test
    void 第三层_绕过代码检查_只读账号也改不了数据() {
        JdbcTemplate jdbc = new JdbcTemplate(reader);
        assertThrows(DataAccessException.class, () -> jdbc.update("DELETE FROM orders"));
        assertThrows(DataAccessException.class, () -> jdbc.update("DROP TABLE orders"));
        assertEquals(3, countOrders());
    }

    @Test
    void 只读账号看不到调用日志() {
        assertThrows(DataAccessException.class, () -> new JdbcTemplate(reader).queryForList("SELECT * FROM askdb_meta.query_log"));
    }

    @Test
    void 读取表结构和注释() {
        List<TableInfo> tables = new SchemaReader(reader).readAll();
        assertEquals(8, tables.size());
        TableInfo orders = tables.stream().filter(t -> t.name().equals("orders")).findFirst().orElseThrow();
        assertEquals("订单表", orders.comment());
        assertTrue(orders.columns().stream().anyMatch(c -> c.comment().contains("关联 customers.customer_id")));
    }

    @Test
    void Agent工具在真实数据上可用() {
        AgentTools tools = new AgentTools(new SchemaReader(reader), new SchemaFormatter(), executor, AskProgress.NONE, 10);

        String values = tools.columnValues("orders", "order_status");
        assertTrue(values.startsWith("delivered（2 次）"), values);
        assertTrue(tools.columnValues("orders", "no_such_column").startsWith("没有这个列"));

        assertTrue(tools.runSql("SELECT order_id FROM orders WHERE customer_id = 'c1'").startsWith("共 2 行"));
        assertTrue(tools.runSql("DELETE FROM orders").startsWith("执行失败"));
        assertTrue(tools.describeTables(List.of("orders")).startsWith("orders -- 订单表"));
        assertEquals(5, tools.calls().size());
        assertEquals(3, countOrders());
    }

    private static int countOrders() {
        return executor.query("SELECT COUNT(*) FROM orders").rows().get(0).get(0) instanceof Number n ? n.intValue() : -1;
    }

    /** 和正式配置一样带上 useInformationSchema，否则读不到表注释 */
    private static DataSource dataSource(String user, String password) {
        String url = MYSQL.getJdbcUrl();
        url += (url.contains("?") ? "&" : "?") + "useInformationSchema=true&characterEncoding=utf8";
        return new DriverManagerDataSource(url, user, password);
    }
}
