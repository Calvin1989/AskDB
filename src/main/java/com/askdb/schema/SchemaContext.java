package com.askdb.schema;

import java.util.List;

/**
 * 交给模型的表结构。
 *
 * @param tables      选中的表名
 * @param totalTables 库里一共有几张表（方便显示"8 张里选了 3 张"）
 * @param text        格式化后的表结构文本
 */
public record SchemaContext(List<String> tables, int totalTables, String text) {
}
