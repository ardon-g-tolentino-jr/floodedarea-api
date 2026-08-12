package com.humanworkstream.baha.dto;

import java.util.List;

/**
 * Outcome of a bulk alert operation.
 *
 * @param affected       alerts created (bulk warn) or deactivated (clear warnings)
 * @param missingAreaIds requested area ids that no longer exist. A client's zone list can
 *                       legitimately be a few seconds stale, so those are skipped and reported
 *                       rather than failing the whole batch. Always empty for clear-warnings,
 *                       which matches on existing alerts rather than looking areas up.
 */
public record BulkAlertResult(int affected, List<Integer> missingAreaIds) {
}
