package com.humanworkstream.baha.enumeration;

/**
 * Flood severity. Constant names match the Postgres {@code severity_t} enum labels
 * (and the JSON the UI expects), so they are intentionally lower-case.
 */
public enum Severity {
    high, medium, low
}
