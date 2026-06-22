package com.humanworkstream.baha.service;

import com.humanworkstream.baha.dto.CreateRescueRequest;
import com.humanworkstream.baha.entity.RescueRequest;
import com.humanworkstream.baha.enumeration.RescueStatus;
import com.humanworkstream.baha.repository.RescueRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RescueRequestService {

    /** A repeat SOS from the same sender within this window updates the open request instead of duplicating it. */
    private static final Duration DEDUP_WINDOW = Duration.ofHours(1);
    /** Statuses that count as a still-open request for dedup; a resolved one never blocks a new SOS. */
    private static final List<RescueStatus> ACTIVE = List.of(RescueStatus.pending, RescueStatus.dispatched);

    private final RescueRequestRepository repository;
    private final ActivitySseService sse;

    @Transactional(readOnly = true)
    public List<RescueRequest> findAll(RescueStatus status) {
        return status == null
                ? repository.findAllByOrderByCreatedAtDesc()
                : repository.findByStatusOrderByCreatedAtDesc(status);
    }

    @Transactional
    public RescueRequest create(CreateRescueRequest req, Integer userId) {
        // Dedup: if the sender already has an open request from within the window, refresh it
        // (new location/note + last-requested time) instead of creating a duplicate row.
        Optional<RescueRequest> recent =
                repository.findFirstByUserIdAndStatusInOrderByLastRequestedAtDesc(userId, ACTIVE);
        if (recent.isPresent()
                && recent.get().getLastRequestedAt().isAfter(OffsetDateTime.now().minus(DEDUP_WINDOW))) {
            RescueRequest existing = recent.get();
            existing.setLatitude(req.latitude());
            existing.setLongitude(req.longitude());
            existing.setNote(req.note());
            existing.setDistrict(req.district());
            existing.setLastRequestedAt(OffsetDateTime.now());
            RescueRequest saved = repository.save(existing);
            sse.broadcast("rescue-updated", saved.getId());
            log.info("[rescue] merged repeat within window rescueId={} by userId={}", saved.getId(), userId);
            return saved;
        }

        RescueRequest r = new RescueRequest();
        r.setUserId(userId);
        r.setLatitude(req.latitude());
        r.setLongitude(req.longitude());
        r.setNote(req.note());
        r.setDistrict(req.district());
        r.setStatus(RescueStatus.pending);
        RescueRequest saved = repository.save(r);
        sse.broadcast("rescue-created", saved.getId());
        log.info("[rescue] created rescueId={} by userId={}", saved.getId(), userId);
        return saved;
    }

    @Transactional
    public Optional<RescueRequest> updateStatus(Integer id, RescueStatus status) {
        return repository.findById(id).map(r -> {
            r.setStatus(status);
            return repository.save(r);
        });
    }

    @Transactional
    public boolean delete(Integer id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }
}
