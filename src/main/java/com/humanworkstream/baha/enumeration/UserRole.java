package com.humanworkstream.baha.enumeration;

/**
 * Application role. GUEST is a no-login device session that may report floods and confirm
 * reports, but not send SOS, submit feedback, or view the rescue queue. CITIZEN can additionally
 * do those; ADMIN further manages centers, hotlines, alerts and rescue requests.
 * Names match the Postgres {@code user_role_t} enum labels.
 */
public enum UserRole {
    CITIZEN, ADMIN, GUEST
}
