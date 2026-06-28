package com.humanworkstream.baha.dto;

import com.humanworkstream.baha.enumeration.FeedbackType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A bug report or feature request. The submitter is taken from the JWT (full account or guest),
 *  never the body. Anyone with a session may submit; guests additionally clear a CAPTCHA. */
public record CreateFeedbackRequest(
        @NotNull FeedbackType type,
        @NotBlank @Size(max = 120) String title,
        @Size(max = 2000) String details,
        /** Cloudflare Turnstile token; required for GUEST submissions when Turnstile is enabled. */
        String captchaToken
) {}
