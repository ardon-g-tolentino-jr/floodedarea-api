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

    /** True when an authenticated ADMIN is present. */
    public static boolean isAdmin(Authentication auth) {
        return auth != null
                && auth.getPrincipal() instanceof UserPrincipal p
                && p.isAdmin();
    }
}
