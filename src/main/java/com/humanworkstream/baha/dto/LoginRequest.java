package com.humanworkstream.baha.dto;

import jakarta.validation.constraints.NotBlank;

/** Basic-login credentials. */
public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password
) {}
