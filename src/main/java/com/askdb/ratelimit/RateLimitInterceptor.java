package com.askdb.ratelimit;

import com.askdb.ratelimit.RateLimiter.Decision;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 在提问、反馈接口前检查限流，超出时直接返回 429，不会调到模型。
 * <p>
 * 按 IP 计数。部署在 Nginx 等反向代理后面时，要配置 server.forward-headers-strategy=native，
 * 否则拿到的都是代理的 IP，所有人共用一个额度。
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;

    public RateLimitInterceptor(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        Decision decision = rateLimiter.tryAcquire(request.getRemoteAddr());
        if (decision.allowed()) {
            return true;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"status":429,"title":"Too Many Requests","detail":"提问太频繁了，请 %d 秒后再试。"}"""
                .formatted(decision.retryAfterSeconds()));
        return false;
    }
}
