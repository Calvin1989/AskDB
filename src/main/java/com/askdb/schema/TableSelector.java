package com.askdb.schema;

import com.askdb.schema.TableInfo.ColumnInfo;
import com.askdb.text.TextTokens;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按问题给每张表打分，选出最相关的几张。不调模型、不用向量库，速度快，结果也能解释清楚。
 * <p>
 * 打分：问题和表的"描述"（表名、表注释、字段名、字段注释）切成词后看重合了哪些词。
 * <ul>
 *   <li>每个词按 IDF 加权：只出现在少数表里的词（如"卖家"）比到处都有的词（如"订单"）更有区分度</li>
 *   <li>出现在表名、表注释里的词权重是字段里的 3 倍</li>
 * </ul>
 * 选取，按顺序：
 * <ol>
 *   <li>问题直接提到了表名或表注释里的词（比如"卖家"对应卖家表），这张表一定选上。
 *       只看相对分数会漏：问题里"卖出""商品"让订单明细表分数很高，卖家表就被挤掉了</li>
 *   <li>其余的表，分数不低于最高分的一定比例才选，总数不超过 maxTables</li>
 *   <li>再加上必须带的表（业务术语涉及的表）</li>
 * </ol>
 * 一张都没匹配上时返回全部表，宁可多给也不能漏。
 */
public final class TableSelector {

    private static final double TABLE_WEIGHT = 3.0;
    private static final double COLUMN_WEIGHT = 1.0;
    /** 一个词最多出现在几张表里，才算能指明某张表的词 */
    private static final int DISTINCTIVE_MAX_TABLES = 2;

    private final List<TableInfo> tables;
    /** 每张表里每个词的权重（同一个词取最大权重） */
    private final Map<String, Map<String, Double>> tableTokens = new HashMap<>();
    /** 每张表的表名、表注释切出来的词 */
    private final Map<String, Set<String>> tableNameTokens = new HashMap<>();
    /** 每个词出现在几张表里 */
    private final Map<String, Integer> documentFrequency = new HashMap<>();

    public TableSelector(List<TableInfo> tables) {
        this.tables = List.copyOf(tables);
        for (TableInfo table : tables) {
            Map<String, Double> weights = new HashMap<>();
            String tableText = table.name() + " " + nullToEmpty(table.comment());
            addTokens(weights, tableText, TABLE_WEIGHT);
            tableNameTokens.put(table.name(), TextTokens.of(tableText));
            for (ColumnInfo column : table.columns()) {
                addTokens(weights, column.name() + " " + nullToEmpty(column.comment()), COLUMN_WEIGHT);
            }
            tableTokens.put(table.name(), weights);
            weights.keySet().forEach(t -> documentFrequency.merge(t, 1, Integer::sum));
        }
    }

    /**
     * @param relativeThreshold 分数至少是最高分的多少（0 到 1）才入选
     */
    public List<String> select(String question, Set<String> required, int maxTables, double relativeThreshold) {
        Set<String> queryTokens = TextTokens.of(question);
        List<Map.Entry<String, Double>> scored = new ArrayList<>();
        for (TableInfo table : tables) {
            double score = score(queryTokens, tableTokens.get(table.name()));
            if (score > 0) {
                scored.add(Map.entry(table.name(), score));
            }
        }
        scored.sort(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()));

        Set<String> selected = new LinkedHashSet<>();
        for (Map.Entry<String, Double> e : scored) {
            if (mentionsTable(queryTokens, e.getKey())) {
                selected.add(e.getKey());
            }
        }
        if (!scored.isEmpty()) {
            double cutoff = scored.get(0).getValue() * relativeThreshold;
            for (Map.Entry<String, Double> e : scored) {
                if (selected.size() >= maxTables || e.getValue() < cutoff) {
                    break;
                }
                selected.add(e.getKey());
            }
        }
        for (String table : required) {
            if (tableTokens.containsKey(table)) {
                selected.add(table);
            }
        }
        if (selected.isEmpty()) {
            return tables.stream().map(TableInfo::name).toList();
        }
        return new ArrayList<>(selected);
    }

    /**
     * 问题是否直接提到了这张表：和表名、表注释重合的词里，有只出现在少数表里的词。
     * "订单"这种几乎每张表注释里都有的词不算，否则每张表都会被选上
     */
    private boolean mentionsTable(Set<String> queryTokens, String table) {
        for (String token : tableNameTokens.get(table)) {
            if (queryTokens.contains(token) && documentFrequency.getOrDefault(token, 0) <= DISTINCTIVE_MAX_TABLES) {
                return true;
            }
        }
        return false;
    }

    /** 每张表的得分，调试和测试用 */
    public double score(String question, String table) {
        return score(TextTokens.of(question), tableTokens.getOrDefault(table, Map.of()));
    }

    private double score(Set<String> queryTokens, Map<String, Double> weights) {
        double score = 0;
        for (String token : queryTokens) {
            Double weight = weights.get(token);
            if (weight != null) {
                score += weight * idf(token);
            }
        }
        return score;
    }

    private double idf(String token) {
        int df = documentFrequency.getOrDefault(token, 0);
        return Math.log(1 + (double) tables.size() / Math.max(1, df));
    }

    private static void addTokens(Map<String, Double> weights, String text, double weight) {
        for (String token : TextTokens.of(text)) {
            weights.merge(token, weight, Math::max);
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
