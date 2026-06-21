package com.humanworkstream.baha.service;

import com.humanworkstream.baha.dto.CreateRescueRequest;
import com.humanworkstream.baha.entity.RescueRequest;
import com.humanworkstream.baha.enumeration.RescueStatus;
import com.humanworkstream.baha.repository.RescueRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RescueRequestService {

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
        RescueRequest r = new RescueRequest();
        r.setUserId(userId);
        r.setLatitude(req.latitude());
        r.setLongitude(req.longitude());
        r.setNote(req.note());
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
