package com.askdb.config;

import com.askdb.ratelimit.RateLimitInterceptor;
import com.askdb.ratelimit.RateLimiter;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 给会调用模型、写数据的接口加限流。读设置、看页面不限。 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RateLimiter rateLimiter;
    private final boolean enabled;

    public WebConfig(RateLimiter rateLimiter, AskDbProperties properties) {
        this.rateLimiter = rateLimiter;
        this.enabled = properties.rateLimit().enabled();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (enabled) {
            registry.addInterceptor(new RateLimitInterceptor(rateLimiter))
                    .addPathPatterns("/api/ask", "/api/ask/stream", "/api/feedback", "/api/settings/llm/test");
        }
    }
}
