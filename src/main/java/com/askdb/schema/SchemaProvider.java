package com.askdb.schema;

import java.util.Set;

/**
 * 决定把哪些表的结构交给模型。用配置 askdb.schema.strategy 切换：
 * <ul>
 *   <li>full：全部表（{@link FullSchemaProvider}），作为对照</li>
 *   <li>retrieval：按问题选相关的表（{@link RetrievalSchemaProvider}）</li>
 * </ul>
 */
public interface SchemaProvider {

    /**
     * @param question       用户的问题
     * @param requiredTables 一定要带上的表（比如问题里用到的业务术语涉及的表）
     */
    SchemaContext describe(String question, Set<String> requiredTables);
}
