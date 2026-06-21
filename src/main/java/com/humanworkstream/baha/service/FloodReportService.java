package com.humanworkstream.baha.service;

import com.humanworkstream.baha.dto.CreateReportRequest;
import com.humanworkstream.baha.entity.FloodReport;
import com.humanworkstream.baha.entity.ReportConfirmation;
import com.humanworkstream.baha.enumeration.ReportStatus;
import com.humanworkstream.baha.enumeration.Road;
import com.humanworkstream.baha.enumeration.Severity;
import com.humanworkstream.baha.enumeration.Trend;
import com.humanworkstream.baha.repository.FloodReportRepository;
import com.humanworkstream.baha.repository.ReportConfirmationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FloodReportService {

    private final FloodReportRepository reportRepository;
    private final ReportConfirmationRepository confirmationRepository;
    private final ActivitySseService sse;

    @Transactional(readOnly = true)
    public List<FloodReport> findActive(Severity severity) {
        return severity == null
                ? reportRepository.findByStatusOrderByCreatedAtDesc(ReportStatus.active)
                : reportRepository.findByStatusAndSeverityOrderByCreatedAtDesc(ReportStatus.active, severity);
    }

    @Transactional(readOnly = true)
    public List<FloodReport> findAll() {
        return reportRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Optional<FloodReport> findById(Integer id) {
        return reportRepository.findById(id);
    }

    @Transactional
    public FloodReport create(CreateReportRequest req, Integer reporterUserId) {
        FloodReport r = new FloodReport();
        r.setName(req.name());
        r.setDistrict(req.district());
        r.setLatitude(req.latitude());
        r.setLongitude(req.longitude());
        r.setSeverity(req.severity());
        r.setDepthCm(req.depthCm());
        r.setTrend(req.trend() != null ? req.trend() : Trend.steady);
        r.setRoad(req.road() != null ? req.road() : Road.risky);
        r.setReportsCount(1);
        r.setStatus(ReportStatus.active);
        r.setReporterUserId(reporterUserId);
        FloodReport saved = reportRepository.save(r);
        // The reporter implicitly confirms their own report.
        if (reporterUserId != null) {
            confirmationRepository.save(new ReportConfirmation(saved.getId(), reporterUserId, null));
        }
        sse.broadcast("report-created", saved.getId());
        log.info("[reports] created reportId={} by userId={}", saved.getId(), reporterUserId);
        return saved;
    }

    /** A user confirms an existing report. Idempotent per (report, user); bumps reports_count. */
    @Transactional
    public Optional<FloodReport> confirm(Integer reportId, Integer userId) {
        return reportRepository.findById(reportId).map(report -> {
            if (!confirmationRepository.existsByReportIdAndUserId(reportId, userId)) {
                confirmationRepository.save(new ReportConfirmation(reportId, userId, null));
                report.setReportsCount(report.getReportsCount() + 1);
                report = reportRepository.save(report);
                sse.broadcast("report-confirmed", reportId);
            }
            return report;
        });
    }

    @Transactional
    public Optional<FloodReport> updateStatus(Integer id, ReportStatus status) {
        return reportRepository.findById(id).map(report -> {
            report.setStatus(status);
            FloodReport saved = reportRepository.save(report);
            sse.broadcast("report-updated", id);
            return saved;
        });
    }

    @Transactional
    public boolean delete(Integer id) {
        if (!reportRepository.existsById(id)) return false;
        reportRepository.deleteById(id);
        sse.broadcast("report-deleted", id);
        return true;
    }
}
