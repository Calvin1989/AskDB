package com.askdb.sql;

import java.util.List;

/**
 * 一次查询的结果。
 *
 * @param columns   列名
 * @param rows      每行的值，顺序和 columns 对应
 * @param truncated 结果是否超过行数上限被截断
 */
public record QueryResult(List<String> columns, List<List<Object>> rows, boolean truncated) {
}
