package com.humanworkstream.baha.dto;

/** The session payload returned by every successful auth call. */
public record AuthResponse(
        String token,
        Integer userId,
        String email,
        String name,
        String picture,
        String role,
        boolean isNew
) {}
