package com.humanworkstream.baha.service;

import com.humanworkstream.baha.entity.EvacuationCenter;
import com.humanworkstream.baha.repository.EvacuationCenterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvacuationCenterService {

    private final EvacuationCenterRepository repository;

    @Transactional(readOnly = true)
    public List<EvacuationCenter> findAll() {
        return repository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Optional<EvacuationCenter> findById(Integer id) {
        return repository.findById(id);
    }

    @Transactional
    public EvacuationCenter create(EvacuationCenter center) {
        center.setId(null);
        if (center.getOccupancy() == null) center.setOccupancy(0);
        return repository.save(center);
    }

    @Transactional
    public Optional<EvacuationCenter> patch(Integer id, EvacuationCenter patch) {
        return repository.findById(id).map(c -> {
            if (patch.getName() != null) c.setName(patch.getName());
            if (patch.getLatitude() != null) c.setLatitude(patch.getLatitude());
            if (patch.getLongitude() != null) c.setLongitude(patch.getLongitude());
            if (patch.getCapacity() != null) c.setCapacity(patch.getCapacity());
            if (patch.getOccupancy() != null) c.setOccupancy(patch.getOccupancy());
            if (patch.getContact() != null) c.setContact(patch.getContact());
            return repository.save(c);
        });
    }

    @Transactional
    public boolean delete(Integer id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }
}
