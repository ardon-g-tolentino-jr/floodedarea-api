package com.humanworkstream.baha.dto;

/** Site visit tally returned by the public {@code /stats} endpoints. Mirrors baha-ui {@code VisitStats}. */
public record VisitStatsResponse(long total) {}
