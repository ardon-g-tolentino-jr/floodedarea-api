package com.humanworkstream.baha.repository;

import com.humanworkstream.baha.entity.FloodReport;
import com.humanworkstream.baha.enumeration.ReportStatus;
import com.humanworkstream.baha.enumeration.Severity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface FloodReportRepository extends JpaRepository<FloodReport, Integer> {
    List<FloodReport> findByStatusOrderByCreatedAtDesc(ReportStatus status);
    List<FloodReport> findByStatusAndSeverityOrderByCreatedAtDesc(ReportStatus status, Severity severity);
    List<FloodReport> findAllByOrderByCreatedAtDesc();

    /** Reports created within a window (any status) — powers the water-level history chart. */
    List<FloodReport> findByCreatedAtBetweenOrderByCreatedAtAsc(OffsetDateTime from, OffsetDateTime to);
}
