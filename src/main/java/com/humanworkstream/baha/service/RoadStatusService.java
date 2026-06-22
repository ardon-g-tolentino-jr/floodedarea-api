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

import java.util.List;

/**
 * Derives the flooded-road overlay from active flood reports. Each report whose road is risky or
 * impassable becomes a short segment through the report point. There is no real map-matching — the
 * geometry is synthesized — but, unlike the offline stub, the passability comes from the report's
 * own {@code road} field rather than a severity proxy. Pure read; no state of its own.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoadStatusService {

    private final FloodReportRepository reportRepository;

    /** Half-length of the synthesized segment, in degrees (~150 m). */
    private static final double SEGMENT_OFFSET_DEG = 0.0015;

    @Transactional(readOnly = true)
    public List<RoadSegmentResponse> currentSegments() {
        return reportRepository.findByStatusOrderByCreatedAtDesc(ReportStatus.active).stream()
                .filter(r -> r.getRoad() != Road.passable)
                .map(this::toSegment)
                .toList();
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
