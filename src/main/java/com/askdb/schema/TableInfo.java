package com.askdb.schema;

import java.util.List;

/** 一张表的结构：表名、表注释和字段。 */
public record TableInfo(String name, String comment, List<ColumnInfo> columns) {

    /** 一个字段：名字、类型（如 VARCHAR(32)）、是否主键、注释。 */
    public record ColumnInfo(String name, String type, boolean primaryKey, String comment) {
    }
}
