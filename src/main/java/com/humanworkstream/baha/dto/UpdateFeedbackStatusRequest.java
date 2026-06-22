package com.humanworkstream.baha.dto;

import com.humanworkstream.baha.enumeration.FeedbackStatus;
import jakarta.validation.constraints.NotNull;

/** Admin: accept or decline a feedback item. */
public record UpdateFeedbackStatusRequest(
        @NotNull FeedbackStatus status
) {}
