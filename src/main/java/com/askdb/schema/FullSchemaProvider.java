package com.askdb.schema;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/** 不管问什么，都把全部表交给模型。作为选表效果的对照组。 */
@Component
@ConditionalOnProperty(name = "askdb.schema.strategy", havingValue = "full")
public class FullSchemaProvider implements SchemaProvider {

    private final SchemaReader reader;
    private final SchemaFormatter formatter;

    public FullSchemaProvider(SchemaReader reader, SchemaFormatter formatter) {
        this.reader = reader;
        this.formatter = formatter;
    }

    @Override
    public SchemaContext describe(String question, Set<String> requiredTables) {
        List<TableInfo> tables = reader.readAll();
        return new SchemaContext(tables.stream().map(TableInfo::name).toList(), tables.size(), formatter.format(tables));
    }
}
