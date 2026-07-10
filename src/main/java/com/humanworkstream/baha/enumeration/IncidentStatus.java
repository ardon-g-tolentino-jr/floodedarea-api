package com.humanworkstream.baha.enumeration;

/**
 * Review lifecycle of a news-drafted flood incident. Names match the Postgres
 * {@code incident_status_t} enum labels (and the JSON the UI expects), so they are lower-case.
 * {@code approved} means an admin promoted it to a system flood report; {@code dismissed} drops it.
 */
public enum IncidentStatus {
    pending, approved, dismissed
}
