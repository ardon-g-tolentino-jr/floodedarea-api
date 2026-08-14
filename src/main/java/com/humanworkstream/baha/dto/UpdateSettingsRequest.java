package com.humanworkstream.baha.dto;

/** ADMIN patch payload for {@code PATCH /settings}. A null field leaves that setting unchanged. */
public record UpdateSettingsRequest(Boolean chatEnabled, Boolean socialAutopostFacebook, Boolean socialAutopostX,
                                     Boolean rainActive) {}
