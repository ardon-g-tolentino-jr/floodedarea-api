package com.humanworkstream.baha.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Per-IP rate-limit knobs bound from {@code baha.rate-limit.*}. Each {@code *PerMin} is the
 * sustained per-IP rate (and burst); 0 disables throttling for that endpoint.
 */
@Component
@ConfigurationProperties(prefix = "baha.rate-limit")
@Data
public class RateLimitProperties {
    private boolean enabled = true;
    private int loginPerMin = 10;
    private int googlePerMin = 10;
    private int registerPerMin = 5;
    private int reportPerMin = 20;
    private int rescuePerMin = 6;
}
