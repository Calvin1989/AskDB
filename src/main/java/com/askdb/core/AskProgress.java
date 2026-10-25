package com.askdb.core;

/**
 * 提问过程中每完成一步就通知一次，前端用来实时显示"正在选表 → 正在生成 SQL → 正在执行"。
 * 不需要进度的地方（普通接口、评测）用 {@link #NONE}。
 */
@FunctionalInterface
public interface AskProgress {

    AskProgress NONE = step -> {
    };

    void step(Step step);

    /**
     * @param stage   哪一步：CACHE / GLOSSARY / SCHEMA / EXAMPLES / GENERATE / EXECUTE / FIX
     * @param message 给用户看的说明
     */
    record Step(String stage, String message) {
    }
}
