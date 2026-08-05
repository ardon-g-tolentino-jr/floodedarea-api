package com.humanworkstream.baha.repository;

import com.humanworkstream.baha.entity.FloodProneArea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FloodProneAreaRepository extends JpaRepository<FloodProneArea, Integer> {
    List<FloodProneArea> findByActiveTrueOrderByCreatedAtDesc();
    List<FloodProneArea> findAllByOrderByCreatedAtDesc();
}
