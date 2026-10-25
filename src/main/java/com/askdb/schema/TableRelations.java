package com.askdb.schema;

import com.askdb.schema.TableInfo.ColumnInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 表之间的关联关系，从字段注释里的"关联 表名.字段名"解析出来（Olist 数据没有建外键）。
 * <p>
 * 用途：选表时，问题提到"客户"和"支付"，选出 customers 和 order_payments，
 * 但它们要经过 orders 才能关联上。这里负责把中间的表补进来，否则模型写不出 JOIN。
 */
public final class TableRelations {

    private static final Pattern REFERENCE = Pattern.compile("关联\\s*([A-Za-z_][A-Za-z0-9_]*)\\.([A-Za-z_][A-Za-z0-9_]*)");

    private final Map<String, Set<String>> neighbors = new HashMap<>();

    public TableRelations(Collection<TableInfo> tables) {
        Set<String> names = new HashSet<>();
        tables.forEach(t -> names.add(t.name()));
        for (TableInfo table : tables) {
            neighbors.computeIfAbsent(table.name(), k -> new HashSet<>());
            for (ColumnInfo column : table.columns()) {
                if (column.comment() == null) {
                    continue;
                }
                Matcher m = REFERENCE.matcher(column.comment());
                while (m.find()) {
                    String target = m.group(1);
                    if (names.contains(target) && !target.equals(table.name())) {
                        neighbors.computeIfAbsent(table.name(), k -> new HashSet<>()).add(target);
                        neighbors.computeIfAbsent(target, k -> new HashSet<>()).add(table.name());
                    }
                }
            }
        }
    }

    public Set<String> neighborsOf(String table) {
        return neighbors.getOrDefault(table, Set.of());
    }

    /**
     * 补齐中间表，让选中的表能互相关联。保持原来的顺序，补进来的表排在后面。
     * 找不到关联路径的表保持原样（可能本来就不需要关联）。
     */
    public List<String> connect(List<String> selected) {
        Set<String> result = new LinkedHashSet<>(selected);
        if (selected.size() < 2) {
            return new ArrayList<>(result);
        }
        Set<String> connected = new HashSet<>(List.of(selected.get(0)));
        for (String table : selected.subList(1, selected.size())) {
            List<String> path = shortestPath(table, connected);
            result.addAll(path);
            connected.addAll(path);
            connected.add(table);
        }
        return new ArrayList<>(result);
    }

    /** 从 start 到 targets 中任意一张表的最短路径（不含两端）。走不通时返回空 */
    private List<String> shortestPath(String start, Set<String> targets) {
        if (targets.contains(start)) {
            return List.of();
        }
        Map<String, String> previous = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>(List.of(start));
        previous.put(start, null);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String next : neighborsOf(current)) {
                if (previous.containsKey(next)) {
                    continue;
                }
                previous.put(next, current);
                if (targets.contains(next)) {
                    List<String> path = new ArrayList<>();
                    for (String t = current; t != null && !t.equals(start); t = previous.get(t)) {
                        path.add(t);
                    }
                    return path;
                }
                queue.add(next);
            }
        }
        return List.of();
    }
}
