package com.humanworkstream.baha.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

/** Static helpers for reading the authenticated {@link UserPrincipal} off an {@link Authentication}. */
public final class SecurityUtils {

    private SecurityUtils() {}

    /** The authenticated principal, or 401 if the request is anonymous. */
    public static UserPrincipal requirePrincipal(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal p) {
            return p;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    /** The authenticated caller's user id, or 401. */
    public static int requireUserId(Authentication auth) {
        return requirePrincipal(auth).userId();
    }

    /** The caller's user id when authenticated, or {@code null} for an anonymous request. Used by
     *  public endpoints that personalize their response (e.g. the feedback board's "upvotedByMe"). */
    public static Integer optionalUserId(Authentication auth) {
        return (auth != null && auth.getPrincipal() instanceof UserPrincipal p) ? p.userId() : null;
    }

    /** True when an authenticated ADMIN is present. */
    public static boolean isAdmin(Authentication auth) {
        return auth != null
                && auth.getPrincipal() instanceof UserPrincipal p
                && p.isAdmin();
    }
}
