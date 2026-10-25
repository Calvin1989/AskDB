package com.askdb.eval;

import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

/**
 * 一次评测的汇总，会整体保存成 JSON，方便以后对比不同版本。
 * <p>
 * 准确率给两个：
 * <ul>
 *   <li>严格：结果和标准答案完全一致（不能多列）</li>
 *   <li>宽松：标准答案的列都对，允许模型多查了列</li>
 * </ul>
 * 两者的差距说明有多少题错在"多查了列"，这类问题通常改提示词就能解决。
 */
public record EvalReport(
        String startedAt,
        String model,
        int total,
        int correct,
        double accuracy,
        int correctIgnoringExtraColumns,
        double accuracyIgnoringExtraColumns,
        long avgElapsedMs,
        long avgPromptTokens,
        Map<String, String> byCategory,
        List<CaseOutcome> cases) {

    /**
     * 一道题的结果。
     *
     * @param status 模型那边的状态：SUCCESS / GENERATION_FAILED / REJECTED / EXECUTION_FAILED；
     *               标准答案本身执行失败时为 GOLD_FAILED（说明题目写错了，要修题）
     * @param match  和标准答案的比较结果：EXACT / EXTRA_COLUMNS / NONE
     */
    public record CaseOutcome(
            String id,
            String category,
            String question,
            String goldSql,
            String generatedSql,
            String status,
            String match,
            String error,
            int promptTokens,
            int corrections,
            long elapsedMs) {

        public boolean correct() {
            return ResultComparator.Match.EXACT.name().equals(match);
        }

        public boolean correctIgnoringExtraColumns() {
            return correct() || ResultComparator.Match.EXTRA_COLUMNS.name().equals(match);
        }
    }

    public static EvalReport of(String startedAt, String model, List<CaseOutcome> cases) {
        int total = cases.size();
        int correct = count(cases, CaseOutcome::correct);
        int lenient = count(cases, CaseOutcome::correctIgnoringExtraColumns);

        Map<String, String> byCategory = cases.stream()
                .collect(Collectors.groupingBy(CaseOutcome::category, TreeMap::new, Collectors.toList()))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> ratio(count(e.getValue(), CaseOutcome::correct), e.getValue().size())
                                + "（允许多列 " + count(e.getValue(), CaseOutcome::correctIgnoringExtraColumns) + "）",
                        (a, b) -> a,
                        TreeMap::new));

        return new EvalReport(startedAt, model, total,
                correct, fraction(correct, total),
                lenient, fraction(lenient, total),
                average(cases, CaseOutcome::elapsedMs),
                average(cases, CaseOutcome::promptTokens),
                byCategory, cases);
    }

    public String summary() {
        StringBuilder sb = new StringBuilder("\n========== 评测结果 ==========\n");
        sb.append("模型：").append(model).append('\n');
        sb.append("严格：").append(ratio(correct, total)).append('\n');
        sb.append("允许多列：").append(ratio(correctIgnoringExtraColumns, total)).append('\n');
        byCategory.forEach((category, value) ->
                sb.append(String.format("  %-15s %s%n", category, value)));
        sb.append("平均耗时：").append(avgElapsedMs).append(" ms\n");
        sb.append("平均输入 token：").append(avgPromptTokens).append('\n');
        long fixed = cases.stream().filter(c -> c.correct() && c.corrections() > 0).count();
        if (fixed > 0) {
            sb.append("其中经过自动修正才答对：").append(fixed).append(" 题\n");
        }
        cases.stream().filter(c -> !c.correct()).forEach(c ->
                sb.append(String.format("  ✗ %s [%s/%s] %s%n", c.id(), c.status(), c.match(), c.question())));
        return sb.toString();
    }

    /**
     * 多次运行的汇总：准确率的平均值和范围，以及"有时对有时错"的题。
     * 不稳定的题说明模型在这里拿不准，往往是提示词或表结构说明不够清楚的地方。
     */
    public static String summarizeRuns(List<EvalReport> runs) {
        DoubleSummaryStatistics strict = runs.stream().mapToDouble(EvalReport::accuracy).summaryStatistics();
        DoubleSummaryStatistics lenient = runs.stream()
                .mapToDouble(EvalReport::accuracyIgnoringExtraColumns).summaryStatistics();

        Map<String, Integer> correctTimes = new TreeMap<>();
        Map<String, String> questions = new TreeMap<>();
        for (EvalReport run : runs) {
            for (CaseOutcome c : run.cases()) {
                correctTimes.merge(c.id(), c.correct() ? 1 : 0, Integer::sum);
                questions.putIfAbsent(c.id(), c.question());
            }
        }

        StringBuilder sb = new StringBuilder(String.format("%n========== %d 次汇总 ==========%n", runs.size()));
        sb.append("模型：").append(runs.isEmpty() ? "" : runs.get(0).model()).append('\n');
        sb.append(String.format("严格：平均 %.1f%%（%.1f%% ~ %.1f%%）%n",
                100 * strict.getAverage(), 100 * strict.getMin(), 100 * strict.getMax()));
        sb.append(String.format("允许多列：平均 %.1f%%（%.1f%% ~ %.1f%%）%n",
                100 * lenient.getAverage(), 100 * lenient.getMin(), 100 * lenient.getMax()));
        sb.append(String.format("平均输入 token：%d%n",
                (long) runs.stream().mapToLong(EvalReport::avgPromptTokens).average().orElse(0)));
        correctTimes.forEach((id, times) -> {
            if (times > 0 && times < runs.size()) {
                sb.append(String.format("  不稳定 %s（%d/%d 次对）%s%n", id, times, runs.size(), questions.get(id)));
            }
        });
        return sb.toString();
    }

    private static int count(List<CaseOutcome> cases, Predicate<CaseOutcome> condition) {
        return (int) cases.stream().filter(condition).count();
    }

    private static long average(List<CaseOutcome> cases, ToLongFunction<CaseOutcome> value) {
        return cases.isEmpty() ? 0 : cases.stream().mapToLong(value).sum() / cases.size();
    }

    private static double fraction(int part, int total) {
        return total == 0 ? 0 : (double) part / total;
    }

    private static String ratio(int part, int total) {
        return String.format("%d/%d = %.1f%%", part, total, 100 * fraction(part, total));
    }
}
