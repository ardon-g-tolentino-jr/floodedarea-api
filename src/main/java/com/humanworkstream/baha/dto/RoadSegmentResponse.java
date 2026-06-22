package com.humanworkstream.baha.dto;

import com.humanworkstream.baha.enumeration.Road;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A flooded / risky road segment, synthesized from a non-passable flood report. Shape mirrors the
 * baha-ui {@code RoadSegment} type so it maps 1:1 onto the client.
 */
public record RoadSegmentResponse(
        Integer id,
        String name,
        Road status,
        List<Point> path,
        Integer reportsCount,
        OffsetDateTime updatedAt
) {
    /** A lat/lng point on the segment path (baha-ui {@code LatLng}). */
    public record Point(double lat, double lng) {}
}
