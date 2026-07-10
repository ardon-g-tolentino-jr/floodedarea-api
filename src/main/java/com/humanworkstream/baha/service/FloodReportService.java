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
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class FloodReportService {

    private final FloodReportRepository reportRepository;
    private final ReportConfirmationRepository confirmationRepository;
    private final ActivitySseService sse;
    private final GeofenceProperties geofence;
    private final TurnstileVerifier turnstile;

    private static final Set<String> ALLOWED_MIME = Set.of("image/jpeg", "image/png", "image/webp");
    private static final int MAX_PHOTO_BYTES = 900_000; // ~0.9 MB decoded; client sends ~300 KB
    private static final Pattern DATA_URL =
            Pattern.compile("^data:(image/[a-zA-Z0-9.+-]+);base64,(.+)$", Pattern.DOTALL);

    /** Small carrier for the photo endpoint. */
    public record ReportPhoto(byte[] bytes, String mime) {}

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
        // A "system" report is honored only for ADMIN callers (news/other-source incidents).
        boolean isSystem = Boolean.TRUE.equals(req.system()) && role == UserRole.ADMIN;
        // Reject coordinates outside the service area — cheap guard against junk/abuse. System
        // reports (admin, from news/other sources) may be anywhere — skip the coverage check.
        if (!isSystem && geofence.isEnabled() && !geofence.contains(req.latitude(), req.longitude())) {
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
        // Denormalized author trust for the UI: 'system' = admin-logged authoritative report;
        // else guest (no-login, provisional) vs verified (real account).
        r.setReporterTrust(isSystem ? "system" : (role == UserRole.GUEST ? "guest" : "verified"));
        // Admin/system reports may carry a source report time + a custom lifetime; citizen reports
        // ignore them (reportedAt then defaults to createdAt via @PrePersist).
        if (isSystem) {
            if (req.reportedAt() != null) r.setReportedAt(req.reportedAt());
            if (req.ttlMinutes() != null && req.ttlMinutes() > 0) r.setTtlMinutes(req.ttlMinutes());
        }
        FloodReport saved = reportRepository.save(r);
        // Optional photo → store the decoded bytes in the DB (bytes + mime written together).
        if (req.photoDataUrl() != null && !req.photoDataUrl().isBlank()) {
            ReportPhoto photo = decodePhoto(req.photoDataUrl());
            reportRepository.setPhoto(saved.getId(), photo.bytes(), photo.mime());
            saved.setPhotoMime(photo.mime()); // so the create response carries photoUrl (no refetch)
        }
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

    /** Decode + validate a client photo data-URL ("data:image/…;base64,…"). */
    private ReportPhoto decodePhoto(String dataUrl) {
        Matcher m = DATA_URL.matcher(dataUrl.trim());
        if (!m.matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photo must be an image data URL.");
        }
        String mime = m.group(1).toLowerCase();
        if (!ALLOWED_MIME.contains(mime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported image type.");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(m.group(2));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photo is not valid base64.");
        }
        if (bytes.length == 0 || bytes.length > MAX_PHOTO_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Photo is too large.");
        }
        return new ReportPhoto(bytes, mime);
    }

    /** Read a report's stored photo bytes + mime; empty when it has none. */
    @Transactional(readOnly = true)
    public Optional<ReportPhoto> photo(Integer id) {
        return reportRepository.findPhotoById(id)
                .map(v -> new ReportPhoto(v.getPhoto(), v.getPhotoMime()));
    }
}
