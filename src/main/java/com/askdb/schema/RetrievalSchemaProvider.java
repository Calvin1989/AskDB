package com.askdb.schema;

import com.askdb.config.AskDbProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 按问题选出相关的表，再补齐关联需要的中间表，只把这些表交给模型。
 * 表越多，省下的 token 越多，模型也越不容易被无关的表带偏。
 */
@Component
@ConditionalOnProperty(name = "askdb.schema.strategy", havingValue = "retrieval", matchIfMissing = true)
public class RetrievalSchemaProvider implements SchemaProvider {

    private final SchemaReader reader;
    private final SchemaFormatter formatter;
    private final int maxTables;
    private final double relativeThreshold;

    /** 表结构不变时复用打分器和关联关系，不用每次重新计算 */
    private volatile Index index;

    private record Index(List<TableInfo> tables, TableSelector selector, TableRelations relations,
                         Map<String, TableInfo> byName) {
    }

    public RetrievalSchemaProvider(SchemaReader reader, SchemaFormatter formatter, AskDbProperties properties) {
        this.reader = reader;
        this.formatter = formatter;
        this.maxTables = properties.schema().maxTables();
        this.relativeThreshold = properties.schema().relativeThreshold();
    }

    @Override
    public SchemaContext describe(String question, Set<String> requiredTables) {
        Index idx = index();
        List<String> selected = idx.selector().select(question, requiredTables, maxTables, relativeThreshold);
        List<String> connected = idx.relations().connect(selected);
        List<TableInfo> chosen = connected.stream().map(idx.byName()::get).toList();
        return new SchemaContext(connected, idx.tables().size(), formatter.format(chosen));
    }

    private Index index() {
        List<TableInfo> tables = reader.readAll();
        Index current = index;
        // SchemaReader 有缓存，表结构没变时拿到的是同一个列表
        if (current == null || current.tables() != tables) {
            current = new Index(tables, new TableSelector(tables), new TableRelations(tables),
                    tables.stream().collect(Collectors.toMap(TableInfo::name, Function.identity())));
            index = current;
        }
        return current;
    }
}
