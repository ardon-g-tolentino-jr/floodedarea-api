package com.humanworkstream.baha.dto;

import com.humanworkstream.baha.enumeration.Road;
import com.humanworkstream.baha.enumeration.Severity;
import com.humanworkstream.baha.enumeration.Trend;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Citizen-submitted flood report. The reporter is taken from the JWT, never the body. */
public record CreateReportRequest(
        @NotBlank String name,
        @NotBlank String district,
        @NotNull Double latitude,
        @NotNull Double longitude,
        @NotNull Severity severity,
        @NotNull Integer depthCm,
        Trend trend,
        Road road
) {}
