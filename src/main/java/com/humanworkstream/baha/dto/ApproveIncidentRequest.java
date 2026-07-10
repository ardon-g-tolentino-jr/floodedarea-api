package com.humanworkstream.baha.dto;

import com.humanworkstream.baha.enumeration.Road;
import com.humanworkstream.baha.enumeration.Severity;
import com.humanworkstream.baha.enumeration.Trend;

import java.time.OffsetDateTime;

/**
 * Optional edits an admin applies when approving a draft incident (e.g. correcting a mis-geocoded
 * location or bumping the severity before it goes live). Every field is optional — any left null
 * falls back to the stored draft's value. Mirrors the editable subset of {@code CreateReportRequest}.
 */
public record ApproveIncidentRequest(
        String name,
        String district,
        Double latitude,
        Double longitude,
        Severity severity,
        Integer depthCm,
        Trend trend,
        Road road,
        OffsetDateTime reportedAt,
        Integer ttlMinutes
) {
    /** An all-null request, used when the admin approves a draft as-is (no body). */
    public static ApproveIncidentRequest empty() {
        return new ApproveIncidentRequest(null, null, null, null, null, null, null, null, null, null);
    }
}
