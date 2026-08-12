package com.humanworkstream.baha.service;

import com.humanworkstream.baha.dto.BulkAlertResult;
import com.humanworkstream.baha.dto.BulkWarnAreasRequest;
import com.humanworkstream.baha.dto.ClearAreaWarningsRequest;
import com.humanworkstream.baha.dto.WarnAreaRequest;
import com.humanworkstream.baha.entity.FloodAlert;
import com.humanworkstream.baha.entity.FloodProneArea;
import com.humanworkstream.baha.enumeration.AlertLevel;
import com.humanworkstream.baha.repository.FloodAlertRepository;
import com.humanworkstream.baha.repository.FloodProneAreaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FloodProneAreaService {

    /** Ceiling on one bulk warn/clear. The nationwide seed is 399 zones, so this is headroom
     *  rather than a real constraint — it exists to bound the blast radius of a malformed call. */
    private static final int MAX_BULK_AREAS = 500;

    private final FloodProneAreaRepository repository;
    private final FloodAlertRepository alertRepository;
    private final ActivitySseService sse;

    @Transactional(readOnly = true)
    public List<FloodProneArea> findActive() {
        return repository.findByActiveTrueOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<FloodProneArea> findAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public FloodProneArea create(FloodProneArea area) {
        area.setId(null);
        if (area.getActive() == null) area.setActive(true);
        return repository.save(area);
    }

    @Transactional
    public Optional<FloodProneArea> patch(Integer id, FloodProneArea patch) {
        return repository.findById(id).map(a -> {
            if (patch.getName() != null) a.setName(patch.getName());
            if (patch.getDistrict() != null) a.setDistrict(patch.getDistrict());
            if (patch.getLatitude() != null) a.setLatitude(patch.getLatitude());
            if (patch.getLongitude() != null) a.setLongitude(patch.getLongitude());
            if (patch.getRadiusM() != null) a.setRadiusM(patch.getRadiusM());
            if (patch.getLevel() != null) a.setLevel(patch.getLevel());
            if (patch.getNote() != null) a.setNote(patch.getNote());
            if (patch.getActive() != null) a.setActive(patch.getActive());
            return repository.save(a);
        });
    }

    @Transactional
    public boolean delete(Integer id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }

    /**
     * Manually trigger a warning for a zone. Reuses the existing {@link FloodAlert} pipeline —
     * the new alert row is geo-stamped from the zone so the client can proximity-gate it, and
     * broadcasts over the same {@code alert-created} SSE event the ribbon already listens for.
     */
    @Transactional
    public FloodAlert warn(Integer id, WarnAreaRequest req) {
        FloodProneArea area = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flood-prone area not found."));

        FloodAlert saved = alertRepository.save(buildWarning(area, req.title(), req.body(), req.level()));

        area.setLastWarnedAt(OffsetDateTime.now());
        repository.save(area);

        sse.broadcast("alert-created", saved.getId());
        return saved;
    }

    /**
     * Warn every listed area in one transaction and — crucially — one SSE broadcast.
     *
     * <p>Before this existed the per-zone endpoint was the client's only option, so a region-wide
     * warn meant one HTTP request <em>and one broadcast</em> per zone. Every connected client
     * refetches on every broadcast, so warning an island group (271 seeded zones for Luzon) meant
     * 271 round trips and 271 fan-outs. This collapses both to one.
     *
     * <p>The broadcast deliberately reuses the existing {@code alert-created} event name: the web
     * client subscribes to a fixed allowlist of event names, so a new name would be silently
     * ignored by every already-deployed client and the ribbon would never refresh.
     *
     * <p>Ids that no longer exist are skipped and returned in {@code missingAreaIds} rather than
     * failing the batch — an admin's zone list can legitimately be a few seconds stale, and losing
     * 270 valid warnings because one zone was just deleted would be the wrong trade.
     */
    @Transactional
    public BulkAlertResult warnBulk(BulkWarnAreasRequest req) {
        List<Integer> ids = sanitizeIds(req == null ? null : req.areaIds());

        List<FloodProneArea> areas = repository.findAllById(ids);
        Set<Integer> found = areas.stream().map(FloodProneArea::getId).collect(Collectors.toSet());
        List<Integer> missing = ids.stream().filter(id -> !found.contains(id)).toList();
        if (areas.isEmpty()) return new BulkAlertResult(0, missing);

        List<FloodAlert> alerts = areas.stream()
                .map(a -> buildWarning(a, req.title(), req.body(), req.level()))
                .toList();
        alertRepository.saveAll(alerts);

        OffsetDateTime now = OffsetDateTime.now();
        areas.forEach(a -> a.setLastWarnedAt(now));
        repository.saveAll(areas);

        sse.broadcast("alert-created", alerts.size());
        return new BulkAlertResult(alerts.size(), missing);
    }

    /**
     * Deactivate the standing warnings raised from the listed areas — the undo for
     * {@link #warnBulk}. Warnings created this way stay {@code active} until something clears them,
     * so a bulk warn without a bulk undo would just accumulate.
     *
     * <p>Matches on {@code FloodAlert.areaId}, so a global alert (null {@code areaId}) or a warning
     * from an area outside the list can never be caught in the sweep.
     */
    @Transactional
    public BulkAlertResult clearWarnings(ClearAreaWarningsRequest req) {
        List<Integer> ids = sanitizeIds(req == null ? null : req.areaIds());

        List<FloodAlert> stale = alertRepository.findByActiveTrueAndAreaIdIn(ids);
        if (stale.isEmpty()) return new BulkAlertResult(0, List.of());

        stale.forEach(a -> a.setActive(false));
        alertRepository.saveAll(stale);

        sse.broadcast("alert-deactivated", stale.size());
        return new BulkAlertResult(stale.size(), List.of());
    }

    /** Null-safe, de-duplicated, size-capped area-id list. Rejects an empty request outright — a
     *  bulk call with nothing to act on is a client bug, not a no-op worth pretending succeeded. */
    private List<Integer> sanitizeIds(List<Integer> raw) {
        List<Integer> ids = raw == null ? List.<Integer>of()
                : raw.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "areaIds must not be empty.");
        }
        if (ids.size() > MAX_BULK_AREAS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Too many areas in one request (max " + MAX_BULK_AREAS + ").");
        }
        return ids;
    }

    /**
     * Build (but don't save) the geo-stamped warning for {@code area}, applying the caller's
     * optional overrides. Shared by the single-zone and bulk paths so the copy an admin sees can
     * never depend on which one they used.
     */
    private FloodAlert buildWarning(FloodProneArea area, String title, String body, AlertLevel level) {
        FloodAlert alert = new FloodAlert();
        alert.setTitle(title != null && !title.isBlank()
                ? title : "Flood-prone area: " + area.getName());
        alert.setBody(body != null && !body.isBlank()
                ? body
                : (area.getNote() != null && !area.getNote().isBlank()
                        ? area.getNote()
                        : "Rising water reported near " + area.getName() + ". Avoid the area if possible."));
        alert.setRegion(area.getDistrict());
        alert.setLevel(level != null ? level : area.getLevel());
        alert.setActive(true);
        alert.setLatitude(area.getLatitude());
        alert.setLongitude(area.getLongitude());
        alert.setRadiusM(area.getRadiusM());
        alert.setAreaId(area.getId());
        return alert;
    }
}
