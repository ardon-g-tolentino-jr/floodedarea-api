package com.humanworkstream.baha.dto;

import com.humanworkstream.baha.enumeration.Road;
import com.humanworkstream.baha.enumeration.Severity;
import com.humanworkstream.baha.enumeration.Trend;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/** Citizen-submitted flood report. The reporter is taken from the JWT, never the body. */
public record CreateReportRequest(
        @NotBlank String name,
        @NotBlank String district,
        @NotNull Double latitude,
        @NotNull Double longitude,
        @NotNull Severity severity,
        @NotNull Integer depthCm,
        Trend trend,
        Road road,
        /** Cloudflare Turnstile token; required for GUEST submissions when Turnstile is enabled. */
        String captchaToken,
        /** Optional compressed JPEG data-URL ("data:image/…;base64,…"). Bounded to keep the JSON body
         *  small; the client already compresses to ~300 KB. */
        @Size(max = 1_400_000, message = "Photo is too large.") String photoDataUrl,
        /** Admin-only: tag this as an authoritative "system" report (news/other sources). */
        Boolean system,
        /** Admin/system reports only: when the source reported the flood. Defaults to now. */
        OffsetDateTime reportedAt,
        /** Admin/system reports only: how long the report stays live, in minutes (severity default when null). */
        Integer ttlMinutes
) {}
