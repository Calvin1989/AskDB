package com.askdb.ratelimit;

import com.askdb.config.AskDbProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Redis 版滑动窗口限流，多台服务器共享计数。
 * <p>
 * 每个 key 对应一个有序集合（ZSET），成员是每次请求，分数是请求时间。
 * "删掉窗口外的 → 数一数 → 没超就加一条"这三步放在一个 Lua 脚本里执行，
 * Redis 单线程执行脚本，保证并发请求时不会多放进来。
 * <p>
 * Redis 出问题时放行（只打警告）：限流是保护措施，不能因为它挂了让整个服务不可用。
 */
@Component
@ConditionalOnProperty(name = "askdb.rate-limit.store", havingValue = "redis")
public class RedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);
    private static final String KEY_PREFIX = "askdb:rate:";

    /** 返回 0 表示放行；否则返回还要等多少毫秒 */
    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
            if redis.call('ZCARD', key) < limit then
              redis.call('ZADD', key, now, ARGV[4])
              redis.call('PEXPIRE', key, window)
              return 0
            end
            local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
            return tonumber(oldest[2]) + window - now
            """, Long.class);

    private final StringRedisTemplate redis;
    private final int limit;
    private final long windowMillis;

    public RedisRateLimiter(StringRedisTemplate redis, AskDbProperties properties) {
        this.redis = redis;
        this.limit = properties.rateLimit().requests();
        this.windowMillis = properties.rateLimit().window().toMillis();
    }

    @Override
    public Decision tryAcquire(String key) {
        try {
            long now = System.currentTimeMillis();
            Long wait = redis.execute(SCRIPT, List.of(KEY_PREFIX + key),
                    String.valueOf(now), String.valueOf(windowMillis), String.valueOf(limit),
                    now + "-" + UUID.randomUUID());
            return (wait == null || wait <= 0) ? Decision.ALLOW : Decision.denyFor(wait);
        } catch (Exception e) {
            log.warn("限流检查失败，本次放行：{}", e.getMessage());
            return Decision.ALLOW;
        }
    }
}
