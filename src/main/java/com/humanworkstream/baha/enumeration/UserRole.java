package com.humanworkstream.baha.enumeration;

/**
 * Application role. CITIZEN can report floods, confirm reports and send SOS;
 * ADMIN additionally manages centers, hotlines, alerts and rescue requests.
 * Names match the Postgres {@code user_role_t} enum labels.
 */
public enum UserRole {
    CITIZEN, ADMIN
}
