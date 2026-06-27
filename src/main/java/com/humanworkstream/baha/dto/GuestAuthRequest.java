package com.humanworkstream.baha.dto;

import jakarta.validation.constraints.NotBlank;

/** Body for POST /auth/guest — a stable per-device id minted and persisted by the client. */
public record GuestAuthRequest(@NotBlank String deviceId) {}
