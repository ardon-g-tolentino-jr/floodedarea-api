package com.humanworkstream.baha.service;

import com.humanworkstream.baha.config.GeofenceProperties;
import com.humanworkstream.baha.dto.CreateReportRequest;
import com.humanworkstream.baha.entity.FloodReport;
import com.humanworkstream.baha.entity.ReportConfirmation;
import com.humanworkstream.baha.enumeration.ReportStatus;
import com.humanworkstream.baha.enumeration.Road;
import com.humanworkstream.baha.enumeration.Severity;
import com.humanworkstream.baha.enumeration.Trend;
import com.humanworkstream.baha.enumeration.UserRole;
import com.humanworkstream.baha.repository.FloodReportRepository;
import com.humanworkstream.baha.repository.ReportConfirmationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FloodReportService {

    private final FloodReportRepository reportRepository;
    private final ReportConfirmationRepository confirmationRepository;
    private final ActivitySseService sse;
    private final GeofenceProperties geofence;
    private final TurnstileVerifier turnstile;

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

    /** Reports created in [from, to) regardless of status — for the water-level history chart. */
    @Transactional(readOnly = true)
    public List<FloodReport> history(OffsetDateTime from, OffsetDateTime to) {
        return reportRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(from, to);
    }

    @Transactional(readOnly = true)
    public Optional<FloodReport> findById(Integer id) {
        return reportRepository.findById(id);
    }

    @Transactional
    public FloodReport create(CreateReportRequest req, Integer reporterUserId, UserRole role, String clientIp) {
        // Reject coordinates outside the service area — cheap guard against junk/abuse.
        if (geofence.isEnabled() && !geofence.contains(req.latitude(), req.longitude())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "That location is outside the coverage area (Metro Manila & Rizal).");
        }
        // No-login guests must clear CAPTCHA when Turnstile is configured (no-op otherwise).
        if (role == UserRole.GUEST && !turnstile.verify(req.captchaToken(), clientIp)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Could not verify you are human. Please try again.");
        }
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
        // Denormalized author trust for the UI: guest reports render as provisional/unverified.
        r.setReporterTrust(role == UserRole.GUEST ? "guest" : "verified");
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

    /**
     * Delete a report. Admins may remove any report; a non-admin may remove only their own
     * (matched on reporterUserId). Returns false if the report doesn't exist; throws 403 when a
     * non-owner non-admin attempts it.
     */
    @Transactional
    public boolean delete(Integer id, int userId, boolean isAdmin) {
        FloodReport report = reportRepository.findById(id).orElse(null);
        if (report == null) return false;
        if (!isAdmin && (report.getReporterUserId() == null || report.getReporterUserId() != userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only remove your own report.");
        }
        reportRepository.deleteById(id);
        sse.broadcast("report-deleted", id);
        log.info("[reports] deleted reportId={} by userId={} admin={}", id, userId, isAdmin);
        return true;
    }
}
