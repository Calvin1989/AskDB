package com.askdb.examples;

import com.askdb.text.TextTokens;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 从示例库里找和当前问题相似的示例。
 * 相似度用两个问题切词后的重合程度（Jaccard），和选表用的是同一套切词。
 */
public final class ExampleMatcher {

    private ExampleMatcher() {
    }

    public static List<Example> findSimilar(List<Example> examples, String question, int limit, double minSimilarity) {
        Set<String> query = TextTokens.of(question);
        String normalized = normalize(question);
        record Scored(Example example, double similarity) {
        }
        return examples.stream()
                // 完全相同的问题不当示例：否则评测时等于直接告诉模型答案
                .filter(e -> !normalize(e.question()).equals(normalized))
                .map(e -> new Scored(e, TextTokens.jaccard(query, TextTokens.of(e.question()))))
                .filter(s -> s.similarity() >= minSimilarity)
                .sorted(Comparator.comparingDouble(Scored::similarity).reversed())
                .limit(limit)
                .map(Scored::example)
                .toList();
    }

    /** 去掉空白和末尾标点、转小写后再比较是否是同一个问题 */
    static String normalize(String question) {
        return question.strip()
                .replaceAll("\\s+", "")
                .replaceAll("[？?。.！!]+$", "")
                .toLowerCase(Locale.ROOT);
    }
}
