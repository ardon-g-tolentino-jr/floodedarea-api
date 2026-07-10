package com.humanworkstream.baha.service;

import com.humanworkstream.baha.dto.ApproveIncidentRequest;
import com.humanworkstream.baha.dto.CreateReportRequest;
import com.humanworkstream.baha.entity.DraftIncident;
import com.humanworkstream.baha.entity.FloodReport;
import com.humanworkstream.baha.enumeration.IncidentStatus;
import com.humanworkstream.baha.enumeration.UserRole;
import com.humanworkstream.baha.repository.DraftIncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * The news-drafted flood-incident review queue. The ingestion job populates it; an admin reviews each
 * draft and either approves it — which promotes it to an authoritative "system" {@link FloodReport}
 * via {@link FloodReportService#create} — or dismisses it. Nothing here reaches the public map without
 * that human step. Structurally the mirror of the feedback moderation queue.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DraftIncidentService {

    private final DraftIncidentRepository incidentRepository;
    private final FloodReportService reportService;
    private final ActivitySseService sse;

    @Transactional(readOnly = true)
    public List<DraftIncident> findByStatus(IncidentStatus status) {
        return incidentRepository.findByStatusOrderByPublishedAtDesc(status);
    }

    /**
     * Promote a pending draft to a system flood report, applying any admin overrides. Delegates the
     * actual report creation (and its own {@code report-created} broadcast + geofence bypass) to
     * {@link FloodReportService#create} with {@code system=true}. Marks the draft approved and records
     * the created report id. Empty when the draft doesn't exist.
     */
    @Transactional
    public Optional<FloodReport> approve(Integer id, ApproveIncidentRequest ov, Integer adminUserId) {
        return incidentRepository.findById(id).map(d -> {
            CreateReportRequest req = new CreateReportRequest(
                    orElse(ov.name(), d.getName()),
                    orElse(ov.district(), d.getDistrict()),
                    orElse(ov.latitude(), d.getLatitude()),
                    orElse(ov.longitude(), d.getLongitude()),
                    orElse(ov.severity(), d.getSeverity()),
                    orElse(ov.depthCm(), d.getDepthCm()),
                    orElse(ov.trend(), d.getTrend()),
                    orElse(ov.road(), d.getRoad()),
                    null,                                              // captchaToken (admin, not a guest)
                    null,                                              // photoDataUrl
                    true,                                              // system report
                    orElse(ov.reportedAt(), d.getPublishedAt()),       // anchor TTL on the article time
                    ov.ttlMinutes()                                    // null ⇒ severity default
            );
            FloodReport created = reportService.create(req, adminUserId, UserRole.ADMIN, null);
            d.setStatus(IncidentStatus.approved);
            d.setCreatedReportId(created.getId());
            incidentRepository.save(d);
            log.info("[incidents] approved incidentId={} -> reportId={} by adminId={}",
                    id, created.getId(), adminUserId);
            return created;
        });
    }

    /** Change a draft's review status (e.g. dismiss). Empty when the draft doesn't exist. */
    @Transactional
    public Optional<DraftIncident> updateStatus(Integer id, IncidentStatus status) {
        return incidentRepository.findById(id).map(d -> {
            d.setStatus(status);
            DraftIncident saved = incidentRepository.save(d);
            log.info("[incidents] incidentId={} status -> {}", id, status);
            return saved;
        });
    }

    /**
     * Persist a freshly ingested draft, deduped on its source URL. Broadcasts {@code incident-drafted}
     * so the admin inbox badge updates live. Empty when a draft for that article already exists.
     */
    @Transactional
    public Optional<DraftIncident> ingest(DraftIncident draft) {
        if (draft.getSourceUrl() == null || incidentRepository.existsBySourceUrl(draft.getSourceUrl())) {
            return Optional.empty();
        }
        draft.setStatus(IncidentStatus.pending);
        DraftIncident saved = incidentRepository.save(draft);
        sse.broadcast("incident-drafted", saved.getId());
        log.info("[incidents] drafted incidentId={} from {}", saved.getId(), saved.getSourceName());
        return Optional.of(saved);
    }

    private static <T> T orElse(T override, T fallback) {
        return override != null ? override : fallback;
    }
}
