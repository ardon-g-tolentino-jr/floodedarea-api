package com.humanworkstream.baha.service.social;

/**
 * Outcome of one publish attempt to a social network.
 *
 * @param success      true when the post was created
 * @param retryable    when {@code !success}, whether the scheduled sweep should try again
 *                     (network/5xx blips) vs. give up immediately (400/401/403 — a bad token or
 *                     payload won't fix itself by retrying)
 * @param externalId   the network's id for the created post, when successful
 * @param externalUrl  a human-followable URL to the created post, when successful
 * @param error        short diagnostic for {@code social_post.last_error}, when not successful
 */
public record PostResult(boolean success, boolean retryable, String externalId, String externalUrl, String error) {

    public static PostResult ok(String externalId, String externalUrl) {
        return new PostResult(true, false, externalId, externalUrl, null);
    }

    public static PostResult retryableFailure(String error) {
        return new PostResult(false, true, null, null, error);
    }

    public static PostResult permanentFailure(String error) {
        return new PostResult(false, false, null, null, error);
    }
}
