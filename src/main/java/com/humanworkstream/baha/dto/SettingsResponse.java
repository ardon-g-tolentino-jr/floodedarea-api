package com.humanworkstream.baha.dto;

/** Global app settings, publicly readable. Mirrors baha-ui {@code AppSettings}. */
public record SettingsResponse(boolean chatEnabled, boolean socialAutopostFacebook, boolean socialAutopostX,
                                boolean rainActive) {}
