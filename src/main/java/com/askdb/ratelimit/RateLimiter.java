package com.askdb.ratelimit;

/**
 * 限流：同一个 key（一般是用户 IP）在一段时间内最多请求几次。
 * 用的是滑动窗口：只看"最近 60 秒"内的请求数，不会出现固定窗口在整点前后各刷一波的问题。
 */
public interface RateLimiter {

    Decision tryAcquire(String key);

    /**
     * @param allowed           这次是否放行
     * @param retryAfterSeconds 不放行时，还要等几秒
     */
    record Decision(boolean allowed, long retryAfterSeconds) {

        public static final Decision ALLOW = new Decision(true, 0);

        public static Decision denyFor(long waitMillis) {
            return new Decision(false, Math.max(1, (waitMillis + 999) / 1000));
        }
    }
}
