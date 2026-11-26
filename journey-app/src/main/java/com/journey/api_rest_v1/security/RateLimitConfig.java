package com.journey.api_rest_v1.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Registers the rate-limit filter ahead of Spring Security, and enables the limiter's clean-up task. */
@Configuration
@EnableScheduling
public class RateLimitConfig {

    /** Spring Security's filter chain runs at order -100; this runs just before it. */
    private static final int ORDER_BEFORE_SECURITY = -110;

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimiter limiter,
            @Value("${journey.rate-limit.enabled:true}") boolean enabled) {
        FilterRegistrationBean<RateLimitFilter> registration =
                new FilterRegistrationBean<>(new RateLimitFilter(limiter, enabled));
        registration.setOrder(ORDER_BEFORE_SECURITY);
        registration.addUrlPatterns("/api/*");
        return registration;
    }
}
