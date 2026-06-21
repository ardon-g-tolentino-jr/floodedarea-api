package com.humanworkstream.baha.security;

import com.humanworkstream.baha.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Per-IP rate limiting for abuse-prone endpoints: the auth surface (brute-force / enumeration)
 * and the public write endpoints (report spam, SOS spam). Runs ahead of the security chain so
 * rejected traffic never reaches the DB. Registered via {@code RateLimitConfig} as a
 * {@code FilterRegistrationBean} (not a {@code @Component}) so it stays out of {@code @WebMvcTest}
 * slices but runs in the full application.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiter;
    private final RateLimitProperties props;

    public RateLimitFilter(RateLimiterService rateLimiter, RateLimitProperties props) {
        this.rateLimiter = rateLimiter;
        this.props = props;
    }

    /** HTTP method + path prefix → the per-minute limit within the current settings. */
    private record Rule(String method, String prefix, ToIntFunction<RateLimitProperties> perMinute) {}

    private static final List<Rule> RULES = List.of(
            new Rule("POST", "/auth/login",    RateLimitProperties::getLoginPerMin),
            new Rule("POST", "/auth/google",   RateLimitProperties::getGooglePerMin),
            new Rule("POST", "/auth/register", RateLimitProperties::getRegisterPerMin),
            new Rule("POST", "/reports",       RateLimitProperties::getReportPerMin),
            new Rule("POST", "/rescue",        RateLimitProperties::getRescuePerMin)
    );

    private static final Duration PERIOD = Duration.ofMinutes(1);

    private Rule ruleFor(String method, String path) {
        for (Rule r : RULES) {
            if (r.method().equals(method) && path.startsWith(r.prefix())) return r;
        }
        return null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Rule rule = props.isEnabled() ? ruleFor(request.getMethod(), request.getServletPath()) : null;
        if (rule != null) {
            int perMinute = rule.perMinute().applyAsInt(props); // 0 = unlimited
            if (perMinute > 0) {
                String key = rule.prefix() + ":" + clientIp(request);
                if (!rateLimiter.tryAcquire(key, perMinute, perMinute, PERIOD)) {
                    response.setStatus(429);
                    response.setHeader("Retry-After", "60");
                    response.setContentType("application/json");
                    response.getWriter().write("{\"message\":\"Too many requests. Please slow down and try again shortly.\"}");
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }

    /** Real client IP behind a reverse proxy (X-Forwarded-For), falling back to the socket. */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
