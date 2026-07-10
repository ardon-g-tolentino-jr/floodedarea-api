package com.humanworkstream.baha.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * News-ingestion tuning for the flood-incident drafter. Bound from {@code baha.incidents.ingest.*}.
 * Disabled by default ({@code enabled=false}) so the app never reaches out to an external news feed
 * unless an operator opts in — the review-queue API works regardless of this.
 */
@Component
@ConfigurationProperties(prefix = "baha.incidents.ingest")
@Data
public class IncidentIngestProperties {

    /** Master switch. When false the scheduled job is a no-op. */
    private boolean enabled = false;

    /** Google News RSS search endpoint (localized to the Philippines, English). */
    private String rssUrl = "https://news.google.com/rss/search";

    /** The search query. `when:1d` keeps it to the last day so the drafter stays timely. */
    private String query = "flood (Metro Manila OR Rizal OR Marikina OR NCR) when:1d";

    /** Cap drafts created per run — a backstop against a noisy news day flooding the queue. */
    private int maxPerRun = 15;

    /** Only draft items whose extraction confidence is at least this (0..1). 0 ⇒ keep all, flag low. */
    private double minConfidence = 0.0;

    /** A descriptive User-Agent — polite, and helps get past outlet bot filters. */
    private String userAgent = "FloodedAreaBot/1.0 (+https://floodedarea.ph; flood incident drafter)";
}
