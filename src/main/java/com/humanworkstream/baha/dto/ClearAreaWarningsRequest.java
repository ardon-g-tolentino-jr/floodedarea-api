package com.humanworkstream.baha.dto;

import java.util.List;

/**
 * Deactivate the standing warnings raised for the listed flood-prone areas — the undo for
 * {@link BulkWarnAreasRequest}. Required and must be non-empty.
 */
public record ClearAreaWarningsRequest(List<Integer> areaIds) {
}
