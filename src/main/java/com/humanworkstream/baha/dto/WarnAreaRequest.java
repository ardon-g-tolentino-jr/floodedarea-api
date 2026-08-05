package com.humanworkstream.baha.dto;

import com.humanworkstream.baha.enumeration.AlertLevel;

/** Manual trigger for a flood-prone area's warning. All fields optional — unset values fall
 *  back to the zone's own title/note/level (see {@code FloodProneAreaService.warn}). */
public record WarnAreaRequest(String title, String body, AlertLevel level) {
}
