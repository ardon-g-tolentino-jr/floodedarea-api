package com.humanworkstream.baha.enumeration;

/** SOS rescue-request lifecycle. Names match the Postgres {@code rescue_status_t} enum labels. */
public enum RescueStatus {
    pending, dispatched, resolved
}
