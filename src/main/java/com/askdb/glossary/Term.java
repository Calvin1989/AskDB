package com.askdb.glossary;

import java.util.List;
import java.util.Locale;

/**
 * 一个业务术语。
 *
 * @param term       术语名，如 GMV
 * @param keywords   问题里出现其中任何一个词，就认为用到了这个术语（不区分大小写）
 * @param definition 口径定义，原样交给模型，要写清楚用哪些字段、怎么算
 * @param tables     这个术语涉及的表，选表时一定会带上
 */
public record Term(String term, List<String> keywords, String definition, List<String> tables) {

    public boolean matches(String question) {
        String q = question.toLowerCase(Locale.ROOT);
        return keywords.stream().anyMatch(k -> q.contains(k.toLowerCase(Locale.ROOT)));
    }
}
