package com.askdb.ratelimit;

import com.askdb.ratelimit.RateLimiter.Decision;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryRateLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final InMemoryRateLimiter limiter = new InMemoryRateLimiter(3, 60_000, now::get);

    @Test
    void 窗口内超过次数就拒绝并告诉还要等多久() {
        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.tryAcquire("ip").allowed());
            now.addAndGet(10_000);
        }
        Decision d = limiter.tryAcquire("ip");
        assertFalse(d.allowed());
        assertEquals(30, d.retryAfterSeconds());
    }

    @Test
    void 最早的请求滑出窗口后又能请求() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("ip");
        }
        now.addAndGet(60_000);
        assertTrue(limiter.tryAcquire("ip").allowed());
    }

    @Test
    void 不同IP分开计数() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("a");
        }
        assertTrue(limiter.tryAcquire("b").allowed());
    }
}
