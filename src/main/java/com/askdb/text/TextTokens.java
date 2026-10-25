package com.askdb.text;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 把一段文本切成用于匹配的"词"。选表和找相似问题都用它。
 * <ul>
 *   <li>中文：没有空格分词，按相邻两个字切（"支付金额" → 支付、付金、金额）。不需要分词库，对短问题效果够用</li>
 *   <li>英文和数字：按单词切，转小写；下划线连接的名字再拆开（order_status → order_status、order、status）</li>
 * </ul>
 */
public final class TextTokens {

    private TextTokens() {
    }

    public static Set<String> of(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null || text.isEmpty()) {
            return tokens;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int i = 0;
        while (i < lower.length()) {
            char c = lower.charAt(i);
            if (isCjk(c)) {
                int end = i;
                while (end < lower.length() && isCjk(lower.charAt(end))) {
                    end++;
                }
                addCjk(lower.substring(i, end), tokens);
                i = end;
            } else if (isWordChar(c)) {
                int end = i;
                while (end < lower.length() && isWordChar(lower.charAt(end))) {
                    end++;
                }
                addWord(lower.substring(i, end), tokens);
                i = end;
            } else {
                i++;
            }
        }
        return tokens;
    }

    /** 两组词的重合程度，0 到 1 */
    public static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int common = 0;
        for (String t : a) {
            if (b.contains(t)) {
                common++;
            }
        }
        return (double) common / (a.size() + b.size() - common);
    }

    private static void addCjk(String run, Set<String> tokens) {
        if (run.length() == 1) {
            tokens.add(run);
            return;
        }
        for (int k = 0; k + 1 < run.length(); k++) {
            tokens.add(run.substring(k, k + 2));
        }
    }

    private static void addWord(String word, Set<String> tokens) {
        tokens.add(word);
        if (word.indexOf('_') >= 0) {
            for (String part : word.split("_")) {
                if (!part.isEmpty()) {
                    tokens.add(part);
                }
            }
        }
    }

    private static boolean isCjk(char c) {
        return Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN;
    }

    private static boolean isWordChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_';
    }
}
