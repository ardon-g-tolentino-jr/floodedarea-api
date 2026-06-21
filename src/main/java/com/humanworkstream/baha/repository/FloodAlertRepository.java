package com.humanworkstream.baha.repository;

import com.humanworkstream.baha.entity.FloodAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FloodAlertRepository extends JpaRepository<FloodAlert, Integer> {
    List<FloodAlert> findByActiveTrueOrderByCreatedAtDesc();
    List<FloodAlert> findAllByOrderByCreatedAtDesc();
}
