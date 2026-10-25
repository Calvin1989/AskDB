package com.askdb.ratelimit;

import com.askdb.config.AskDbProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 内存版滑动窗口限流：每个 key 记下最近请求的时间点，超出窗口的丢掉。
 * 只在单台服务器上有效（每台机器各算各的）；多台部署时用 {@link RedisRateLimiter}。
 */
@Component
@ConditionalOnProperty(name = "askdb.rate-limit.store", havingValue = "memory", matchIfMissing = true)
public class InMemoryRateLimiter implements RateLimiter {

    private final int limit;
    private final long windowMillis;
    private final LongSupplier clock;
    private final Map<String, Deque<Long>> requests = new ConcurrentHashMap<>();
    /** 记录的 IP 太多时，清掉窗口内已经没有请求的，防止内存一直涨 */
    private static final int CLEANUP_THRESHOLD = 10_000;

    @Autowired
    public InMemoryRateLimiter(AskDbProperties properties) {
        this(properties.rateLimit().requests(), properties.rateLimit().window().toMillis(), System::currentTimeMillis);
    }

    /** 测试时传入假时钟 */
    InMemoryRateLimiter(int limit, long windowMillis, LongSupplier clock) {
        this.limit = limit;
        this.windowMillis = windowMillis;
        this.clock = clock;
    }

    @Override
    public Decision tryAcquire(String key) {
        long now = clock.getAsLong();
        if (requests.size() > CLEANUP_THRESHOLD) {
            requests.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    Long last = e.getValue().peekLast();
                    return last == null || last <= now - windowMillis;
                }
            });
        }
        Deque<Long> times = requests.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst() <= now - windowMillis) {
                times.pollFirst();
            }
            if (times.size() < limit) {
                times.addLast(now);
                return Decision.ALLOW;
            }
            return Decision.denyFor(times.peekFirst() + windowMillis - now);
        }
    }
}
