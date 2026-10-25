package com.askdb.eval;

import com.askdb.sql.QueryResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 判断模型的查询结果和标准答案是否一致（"执行准确率"的判法）。
 * <p>
 * 做法：把标准答案的每一列，配对到模型结果里的某一列，配对后所有行（不管顺序）都对得上，就算一致。
 * <ul>
 *   <li>不管行的顺序、列名、列的顺序</li>
 *   <li>模型自己保留了较少的小数位（比如 ROUND(x, 2)）时，按较少的位数比较；
 *       但标准答案有小数时至少比到 2 位，取整到个位不算对</li>
 *   <li>模型多查了列（比如顺手带上了 COUNT），单独记为 {@link Match#EXTRA_COLUMNS}，
 *       报告里同时给出"严格"和"允许多列"两个准确率</li>
 * </ul>
 * 仍然不检查行的顺序：题目要求"前 5 名"时，只检查是不是这 5 个，不检查排名先后。
 */
public final class ResultComparator {

    /** 最多比到 4 位小数，避免浮点误差 */
    private static final int MAX_SCALE = 4;
    /** 标准答案有小数时，至少比到 2 位 */
    private static final int MIN_SCALE = 2;

    public enum Match {
        /** 列数相同，结果一致 */
        EXACT,
        /** 标准答案的列都对上了，但模型多查了列 */
        EXTRA_COLUMNS,
        /** 不一致 */
        NONE
    }

    private ResultComparator() {
    }

    public static Match compare(QueryResult expected, QueryResult actual) {
        int goldCols = expected.columns().size();
        int predCols = actual.columns().size();
        if (expected.rows().size() != actual.rows().size() || predCols < goldCols) {
            return Match.NONE;
        }

        List<List<Object>> gold = columnsOf(expected);
        List<List<Object>> pred = columnsOf(actual);
        int[] mapping = new int[goldCols];
        boolean[] used = new boolean[predCols];
        if (!assign(0, gold, pred, mapping, used, expected.rows(), actual.rows())) {
            return Match.NONE;
        }
        return predCols == goldCols ? Match.EXACT : Match.EXTRA_COLUMNS;
    }

    /** 回溯：给第 g 列标准答案找一个还没用过、值对得上的模型结果列。列数很少，穷举很快。 */
    private static boolean assign(int g, List<List<Object>> gold, List<List<Object>> pred, int[] mapping,
                                  boolean[] used, List<List<Object>> goldRows, List<List<Object>> predRows) {
        if (g == gold.size()) {
            return rowsMatch(gold, pred, mapping, goldRows, predRows);
        }
        for (int p = 0; p < pred.size(); p++) {
            if (!used[p] && sameValues(gold.get(g), pred.get(p))) {
                used[p] = true;
                mapping[g] = p;
                if (assign(g + 1, gold, pred, mapping, used, goldRows, predRows)) {
                    return true;
                }
                used[p] = false;
            }
        }
        return false;
    }

    /** 单独看一列：两列的值（不管顺序）是否相同。用来先排除不可能的配对。 */
    private static boolean sameValues(List<Object> goldColumn, List<Object> predColumn) {
        int scale = scaleFor(goldColumn, predColumn);
        return sorted(goldColumn, scale).equals(sorted(predColumn, scale));
    }

    /** 列配好以后，按行检查：每一行配对后的值组合，两边（不管顺序）是否相同。 */
    private static boolean rowsMatch(List<List<Object>> gold, List<List<Object>> pred, int[] mapping,
                                     List<List<Object>> goldRows, List<List<Object>> predRows) {
        int[] scales = new int[gold.size()];
        for (int g = 0; g < gold.size(); g++) {
            scales[g] = scaleFor(gold.get(g), pred.get(mapping[g]));
        }
        List<String> goldKeys = new ArrayList<>(goldRows.size());
        List<String> predKeys = new ArrayList<>(predRows.size());
        for (List<Object> row : goldRows) {
            goldKeys.add(rowKey(row, null, scales));
        }
        for (List<Object> row : predRows) {
            predKeys.add(rowKey(row, mapping, scales));
        }
        goldKeys.sort(null);
        predKeys.sort(null);
        return goldKeys.equals(predKeys);
    }

    private static String rowKey(List<Object> row, int[] mapping, int[] scales) {
        StringBuilder sb = new StringBuilder();
        for (int g = 0; g < scales.length; g++) {
            Object value = row.get(mapping == null ? g : mapping[g]);
            sb.append(normalize(value, scales[g])).append('\u0001');
        }
        return sb.toString();
    }

    /**
     * 这一对列按几位小数比较：取两边小数位数较少的一方（模型可能主动四舍五入），
     * 但标准答案有小数时至少 2 位，最多 4 位。
     */
    private static int scaleFor(List<Object> goldColumn, List<Object> predColumn) {
        int gold = maxScale(goldColumn);
        int pred = maxScale(predColumn);
        return Math.min(MAX_SCALE, Math.max(Math.min(gold, pred), Math.min(gold, MIN_SCALE)));
    }

    private static int maxScale(List<Object> column) {
        int max = 0;
        for (Object value : column) {
            if (value instanceof Number number) {
                max = Math.max(max, Math.max(0, decimal(number).stripTrailingZeros().scale()));
            }
        }
        return max;
    }

    private static List<String> sorted(List<Object> column, int scale) {
        List<String> values = new ArrayList<>(column.size());
        for (Object value : column) {
            values.add(normalize(value, scale));
        }
        values.sort(null);
        return values;
    }

    private static String normalize(Object value, int scale) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number number) {
            return decimal(number).setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        }
        return value.toString().trim();
    }

    private static BigDecimal decimal(Number number) {
        return number instanceof BigDecimal bd ? bd : new BigDecimal(number.toString());
    }

    private static List<List<Object>> columnsOf(QueryResult result) {
        int count = result.columns().size();
        List<List<Object>> columns = new ArrayList<>(count);
        for (int c = 0; c < count; c++) {
            List<Object> column = new ArrayList<>(result.rows().size());
            for (List<Object> row : result.rows()) {
                column.add(row.get(c));
            }
            columns.add(column);
        }
        return columns;
    }
}
