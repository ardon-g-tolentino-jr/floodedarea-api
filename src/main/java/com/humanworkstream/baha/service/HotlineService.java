package com.humanworkstream.baha.service;

import com.humanworkstream.baha.entity.Hotline;
import com.humanworkstream.baha.repository.HotlineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class HotlineService {

    private final HotlineRepository repository;

    @Transactional(readOnly = true)
    public List<Hotline> findAll() {
        return repository.findAllByOrderByPositionAsc();
    }

    @Transactional
    public Hotline create(Hotline hotline) {
        hotline.setId(null);
        if (hotline.getPosition() == null) hotline.setPosition((short) 0);
        return repository.save(hotline);
    }

    @Transactional
    public Optional<Hotline> patch(Integer id, Hotline patch) {
        return repository.findById(id).map(h -> {
            if (patch.getName() != null) h.setName(patch.getName());
            if (patch.getDescription() != null) h.setDescription(patch.getDescription());
            if (patch.getPhoneNumber() != null) h.setPhoneNumber(patch.getPhoneNumber());
            if (patch.getPosition() != null) h.setPosition(patch.getPosition());
            return repository.save(h);
        });
    }

    @Transactional
    public boolean delete(Integer id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }
}
