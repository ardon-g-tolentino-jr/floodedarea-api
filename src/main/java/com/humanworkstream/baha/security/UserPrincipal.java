package com.humanworkstream.baha.security;

import com.humanworkstream.baha.enumeration.UserRole;

/**
 * The authenticated caller, derived from a verified JWT and stored as the principal in the
 * Spring {@code SecurityContext}. Carries just enough to authorize and attribute writes:
 * the account id, email and role.
 */
public record UserPrincipal(String email, int userId, UserRole role) {

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
