package com.humanworkstream.baha.dto;

import com.humanworkstream.baha.enumeration.IncidentStatus;
import jakarta.validation.constraints.NotNull;

/** Admin: change a draft incident's review status (e.g. {@code dismissed}). */
public record UpdateIncidentStatusRequest(
        @NotNull IncidentStatus status
) {}
