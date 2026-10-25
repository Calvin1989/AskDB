package com.askdb.schema;

import com.askdb.schema.TableInfo.ColumnInfo;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 通过 JDBC 元数据读取当前库里所有表的结构。
 * 表结构在运行期间基本不变，所以第一次读完就缓存起来；导入新表后调用 refresh()。
 */
@Component
public class SchemaReader {

    private final DataSource dataSource;
    private volatile List<TableInfo> cache;

    public SchemaReader(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<TableInfo> readAll() {
        List<TableInfo> tables = cache;
        if (tables == null) {
            synchronized (this) {
                if (cache == null) {
                    List<TableInfo> loaded = load();
                    // 空库不缓存：导入数据后不用重启也能读到新表
                    if (!loaded.isEmpty()) {
                        cache = loaded;
                    }
                    tables = loaded;
                } else {
                    tables = cache;
                }
            }
        }
        return tables;
    }

    public synchronized void refresh() {
        cache = null;
    }

    private List<TableInfo> load() {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            List<TableInfo> tables = new ArrayList<>();
            try (ResultSet rs = meta.getTables(catalog, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String table = rs.getString("TABLE_NAME");
                    tables.add(new TableInfo(table, rs.getString("REMARKS"), readColumns(meta, catalog, table)));
                }
            }
            return List.copyOf(tables);
        } catch (SQLException e) {
            throw new IllegalStateException("读取表结构失败：" + e.getMessage(), e);
        }
    }

    private List<ColumnInfo> readColumns(DatabaseMetaData meta, String catalog, String table) throws SQLException {
        Set<String> primaryKeys = new HashSet<>();
        try (ResultSet rs = meta.getPrimaryKeys(catalog, null, table)) {
            while (rs.next()) {
                primaryKeys.add(rs.getString("COLUMN_NAME"));
            }
        }

        List<ColumnInfo> columns = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(catalog, null, table, "%")) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                columns.add(new ColumnInfo(
                        name,
                        typeOf(rs.getString("TYPE_NAME"), rs.getInt("COLUMN_SIZE"), rs.getInt("DECIMAL_DIGITS")),
                        primaryKeys.contains(name),
                        rs.getString("REMARKS")));
            }
        }
        return columns;
    }

    /** 只给字符串和小数类型带上长度，INT、DATETIME 这类不需要。 */
    private static String typeOf(String typeName, int size, int digits) {
        return switch (typeName.toUpperCase()) {
            case "VARCHAR", "CHAR" -> typeName + "(" + size + ")";
            case "DECIMAL" -> typeName + "(" + size + "," + digits + ")";
            default -> typeName;
        };
    }
}
