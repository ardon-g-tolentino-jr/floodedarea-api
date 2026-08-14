package com.humanworkstream.baha.dto;

import com.humanworkstream.baha.enumeration.ReportStatus;
import com.humanworkstream.baha.enumeration.Road;
import com.humanworkstream.baha.enumeration.Severity;
import com.humanworkstream.baha.enumeration.Trend;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Admin: correct or resolve an existing flood report. Every field is nullable — a null leaves
 * that column unchanged, so the admin can patch just {@code status}, just {@code depthCm}, or any
 * combination in one call. Replaces the narrower {@code UpdateReportStatusRequest}, which only
 * ever covered {@code status}.
 */
public record UpdateReportRequest(
        ReportStatus status,
        @PositiveOrZero Integer depthCm,
        Road road,
        Severity severity,
        Trend trend,
        /** New effective lifetime in minutes. The UI sends "current effective TTL + 60" per tap of
         *  its "extend 1 hour" control; this DTO just stores whatever value is sent. */
        @Positive Integer ttlMinutes
) {}
