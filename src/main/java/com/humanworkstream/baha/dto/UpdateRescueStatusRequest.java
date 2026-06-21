package com.humanworkstream.baha.dto;

import com.humanworkstream.baha.enumeration.RescueStatus;
import jakarta.validation.constraints.NotNull;

/** Admin: advance a rescue request (pending → dispatched → resolved). */
public record UpdateRescueStatusRequest(
        @NotNull RescueStatus status
) {}
