package com.humanworkstream.baha.enumeration;

/** How an account authenticates. Names match the Postgres {@code auth_provider_t} enum labels. */
public enum AuthProvider {
    LOCAL, GOOGLE
}
