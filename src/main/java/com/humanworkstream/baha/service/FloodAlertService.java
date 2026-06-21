package com.humanworkstream.baha.service;

import com.humanworkstream.baha.entity.FloodAlert;
import com.humanworkstream.baha.repository.FloodAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FloodAlertService {

    private final FloodAlertRepository repository;

    @Transactional(readOnly = true)
    public List<FloodAlert> findActive() {
        return repository.findByActiveTrueOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<FloodAlert> findAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public FloodAlert create(FloodAlert alert) {
        alert.setId(null);
        if (alert.getActive() == null) alert.setActive(true);
        return repository.save(alert);
    }

    @Transactional
    public Optional<FloodAlert> patch(Integer id, FloodAlert patch) {
        return repository.findById(id).map(a -> {
            if (patch.getTitle() != null) a.setTitle(patch.getTitle());
            if (patch.getBody() != null) a.setBody(patch.getBody());
            if (patch.getRegion() != null) a.setRegion(patch.getRegion());
            if (patch.getLevel() != null) a.setLevel(patch.getLevel());
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
}
