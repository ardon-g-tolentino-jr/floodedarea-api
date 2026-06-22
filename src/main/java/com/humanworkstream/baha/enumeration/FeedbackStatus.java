package com.humanworkstream.baha.enumeration;

/** Feedback moderation lifecycle. Names match the Postgres {@code feedback_status_t} enum labels. */
public enum FeedbackStatus {
    pending, accepted, declined
}
