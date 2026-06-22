package com.humanworkstream.baha.service;

import com.humanworkstream.baha.dto.RoadSegmentResponse;
import com.humanworkstream.baha.entity.FloodReport;
import com.humanworkstream.baha.enumeration.ReportStatus;
import com.humanworkstream.baha.enumeration.Road;
import com.humanworkstream.baha.repository.FloodReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Derives the flooded-road overlay from active flood reports. Each report whose road is risky or
 * impassable becomes a short segment through the report point (no real map-matching — the geometry
 * is synthesized).
 *
 * <p>A segment only lingers while people keep reporting that spot: a report's {@code updatedAt}
 * advances every time it's (re)confirmed, and once it has gone quiet for longer than its category's
 * time-to-live the segment drops off the overlay. The window is longer for impassable closures —
 * they clear slowly, and keeping the danger visible errs on the safe side — and shorter for merely
 * risky ones, where conditions change fast and a stale marker is low-stakes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoadStatusService {

    private final FloodReportRepository reportRepository;

    /** Half-length of the synthesized segment, in degrees (~150 m). */
    private static final double SEGMENT_OFFSET_DEG = 0.0015;

    // How long a segment survives without a fresh confirmation, per road-closure category.
    private static final Duration TTL_IMPASSABLE = Duration.ofHours(12);
    private static final Duration TTL_RISKY = Duration.ofHours(6);

    @Transactional(readOnly = true)
    public List<RoadSegmentResponse> currentSegments() {
        OffsetDateTime now = OffsetDateTime.now();
        return reportRepository.findByStatusOrderByCreatedAtDesc(ReportStatus.active).stream()
                .filter(r -> r.getRoad() != Road.passable)
                .filter(r -> isFresh(r, now))
                .map(this::toSegment)
                .toList();
    }

    /** True while the report's last activity is within its category's time-to-live. */
    private boolean isFresh(FloodReport r, OffsetDateTime now) {
        Duration ttl = switch (r.getRoad()) {
            case impassable -> TTL_IMPASSABLE;
            case risky -> TTL_RISKY;
            case passable -> Duration.ZERO; // excluded above; here only for switch exhaustiveness
        };
        return r.getUpdatedAt().isAfter(now.minus(ttl));
    }

    private RoadSegmentResponse toSegment(FloodReport r) {
        // Orient the segment by report-id parity so neighboring pins don't all draw parallel.
        double d = SEGMENT_OFFSET_DEG;
        boolean even = r.getId() % 2 == 0;
        double dx = even ? d : d * 0.4;
        double dy = even ? d * 0.4 : d;
        List<RoadSegmentResponse.Point> path = List.of(
                new RoadSegmentResponse.Point(r.getLatitude() - dy, r.getLongitude() - dx),
                new RoadSegmentResponse.Point(r.getLatitude(), r.getLongitude()),
                new RoadSegmentResponse.Point(r.getLatitude() + dy, r.getLongitude() + dx)
        );
        return new RoadSegmentResponse(r.getId(), r.getName(), r.getRoad(), path, r.getReportsCount(), r.getUpdatedAt());
    }
}
