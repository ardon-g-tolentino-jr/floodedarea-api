package com.humanworkstream.baha.service;

import com.humanworkstream.baha.dto.WarnAreaRequest;
import com.humanworkstream.baha.entity.FloodAlert;
import com.humanworkstream.baha.entity.FloodProneArea;
import com.humanworkstream.baha.repository.FloodAlertRepository;
import com.humanworkstream.baha.repository.FloodProneAreaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FloodProneAreaService {

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

        FloodAlert alert = new FloodAlert();
        alert.setTitle(req.title() != null && !req.title().isBlank()
                ? req.title() : "Flood-prone area: " + area.getName());
        alert.setBody(req.body() != null && !req.body().isBlank()
                ? req.body()
                : (area.getNote() != null && !area.getNote().isBlank()
                        ? area.getNote()
                        : "Rising water reported near " + area.getName() + ". Avoid the area if possible."));
        alert.setRegion(area.getDistrict());
        alert.setLevel(req.level() != null ? req.level() : area.getLevel());
        alert.setActive(true);
        alert.setLatitude(area.getLatitude());
        alert.setLongitude(area.getLongitude());
        alert.setRadiusM(area.getRadiusM());
        alert.setAreaId(area.getId());

        FloodAlert saved = alertRepository.save(alert);

        area.setLastWarnedAt(OffsetDateTime.now());
        repository.save(area);

        sse.broadcast("alert-created", saved.getId());
        return saved;
    }
}
