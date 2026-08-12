package com.humanworkstream.baha.dto;

import com.humanworkstream.baha.enumeration.AlertLevel;

import java.util.List;

/**
 * Warn every listed flood-prone area in a single request.
 *
 * <p>{@code areaIds} is required and must be non-empty. The remaining fields are the same optional
 * overrides as {@link WarnAreaRequest}, applied to every area in the batch — unset values fall back
 * per-area to that area's own name/note/level, exactly as the single-zone warn does.
 */
public record BulkWarnAreasRequest(List<Integer> areaIds, String title, String body, AlertLevel level) {
}
