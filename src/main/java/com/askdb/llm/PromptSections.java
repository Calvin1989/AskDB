package com.askdb.llm;

import com.askdb.examples.Example;
import com.askdb.glossary.Term;

import java.util.List;

/**
 * 拼提示词里的补充部分：业务术语、相似问题的示例。没有内容时返回空字符串，提示词里就没有这一段。
 */
public final class PromptSections {

    private PromptSections() {
    }

    public static String of(List<Term> terms, List<Example> examples) {
        return glossary(terms) + examples(examples);
    }

    static String glossary(List<Term> terms) {
        if (terms.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n业务术语（必须按这里的口径写 SQL）：\n");
        for (Term t : terms) {
            sb.append("- ").append(t.term()).append("：").append(t.definition()).append('\n');
        }
        return sb.toString();
    }

    static String examples(List<Example> examples) {
        if (examples.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n已确认正确的相似问题（可以参考写法，但要按当前问题修改）：\n");
        for (Example e : examples) {
            sb.append("问题：").append(e.question()).append('\n')
              .append("SQL：").append(e.sql().strip()).append("\n\n");
        }
        return sb.toString();
    }
}
