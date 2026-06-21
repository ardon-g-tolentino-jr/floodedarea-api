package com.humanworkstream.baha.dto;

import com.humanworkstream.baha.enumeration.ReportStatus;
import jakarta.validation.constraints.NotNull;

/** Admin: mark a flood report active/resolved. */
public record UpdateReportStatusRequest(
        @NotNull ReportStatus status
) {}
