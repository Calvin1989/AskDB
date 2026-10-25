package com.askdb.sql;

/**
 * SQL 没有通过安全检查，不会被执行。
 * <p>
 * fixable 区分两种情况：
 * <ul>
 *   <li>true：写法问题（解析不了、表名前带了库名），可以让模型改写后再试</li>
 *   <li>false：想修改数据或一次执行多条语句，直接拒绝，不尝试修正</li>
 * </ul>
 */
public class UnsafeSqlException extends RuntimeException {

    private final boolean fixable;

    public UnsafeSqlException(String message, boolean fixable) {
        super(message);
        this.fixable = fixable;
    }

    public boolean fixable() {
        return fixable;
    }
}
