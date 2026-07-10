package com.humanworkstream.baha.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on Spring's {@code @Scheduled} support (used by the flood-news ingestion job). Kept as its
 * own config so scheduling can be reasoned about (and, if ever needed, disabled) in one place.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
