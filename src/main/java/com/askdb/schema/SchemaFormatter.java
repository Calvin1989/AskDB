package com.askdb.schema;

import com.askdb.schema.TableInfo.ColumnInfo;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * 把表结构转成给模型看的文本，格式接近建表语句，模型最熟悉：
 * <pre>
 * orders -- 订单表
 *   order_id VARCHAR(32) PRIMARY KEY -- 订单ID
 *   customer_id VARCHAR(32) -- 下单客户，关联 customers.customer_id
 * </pre>
 */
@Component
public class SchemaFormatter {

    public String format(Collection<TableInfo> tables) {
        return tables.stream()
                .map(this::formatTable)
                .collect(Collectors.joining("\n\n"));
    }

    private String formatTable(TableInfo table) {
        StringBuilder sb = new StringBuilder(table.name()).append(comment(table.comment()));
        for (ColumnInfo column : table.columns()) {
            sb.append("\n  ").append(column.name()).append(' ').append(column.type());
            if (column.primaryKey()) {
                sb.append(" PRIMARY KEY");
            }
            sb.append(comment(column.comment()));
        }
        return sb.toString();
    }

    private static String comment(String text) {
        return (text == null || text.isBlank()) ? "" : " -- " + text.trim();
    }
}
