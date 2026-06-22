package com.humanworkstream.baha.dto;

import com.humanworkstream.baha.enumeration.FeedbackType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A logged-in user's bug report or feature request. The submitter is taken from the JWT, never the body. */
public record CreateFeedbackRequest(
        @NotNull FeedbackType type,
        @NotBlank @Size(max = 120) String title,
        @Size(max = 2000) String details
) {}
