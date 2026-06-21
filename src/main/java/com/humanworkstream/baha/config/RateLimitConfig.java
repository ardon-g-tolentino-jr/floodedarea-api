package com.humanworkstream.baha.config;

import com.humanworkstream.baha.security.RateLimitFilter;
import com.humanworkstream.baha.security.RateLimiterService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Registers {@link RateLimitFilter} ahead of the Spring Security filter chain. */
@Configuration
public class RateLimitConfig {

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
            RateLimiterService rateLimiter, RateLimitProperties props) {
        FilterRegistrationBean<RateLimitFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new RateLimitFilter(rateLimiter, props));
        reg.addUrlPatterns("/auth/*", "/reports/*", "/reports", "/rescue/*", "/rescue");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return reg;
    }
}
