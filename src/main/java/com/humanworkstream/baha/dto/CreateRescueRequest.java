package com.humanworkstream.baha.dto;

import jakarta.validation.constraints.NotNull;

/** SOS — send a rescue request with the caller's location. */
public record CreateRescueRequest(
        @NotNull Double latitude,
        @NotNull Double longitude,
        String note,
        String district
) {}
