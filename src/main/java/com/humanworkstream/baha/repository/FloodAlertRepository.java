package com.humanworkstream.baha.repository;

import com.humanworkstream.baha.entity.FloodAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface FloodAlertRepository extends JpaRepository<FloodAlert, Integer> {
    List<FloodAlert> findByActiveTrueOrderByCreatedAtDesc();
    List<FloodAlert> findAllByOrderByCreatedAtDesc();

    /** Standing warnings raised from any of the given flood-prone areas. A null {@code areaId}
     *  (every global alert) can never match an id, so global alerts are structurally excluded. */
    List<FloodAlert> findByActiveTrueAndAreaIdIn(Collection<Integer> areaIds);
}
