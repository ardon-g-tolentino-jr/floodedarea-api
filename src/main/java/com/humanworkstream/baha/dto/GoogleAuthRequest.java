package com.humanworkstream.baha.dto;

import jakarta.validation.constraints.NotBlank;

/** A Google Sign-In ID token (JWT) issued to the front-end by Google Identity Services. */
public record GoogleAuthRequest(
        @NotBlank String idToken
) {}
