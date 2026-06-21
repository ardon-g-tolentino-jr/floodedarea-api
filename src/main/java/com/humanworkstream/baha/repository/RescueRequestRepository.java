package com.humanworkstream.baha.repository;

import com.humanworkstream.baha.entity.RescueRequest;
import com.humanworkstream.baha.enumeration.RescueStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RescueRequestRepository extends JpaRepository<RescueRequest, Integer> {
    List<RescueRequest> findAllByOrderByCreatedAtDesc();
    List<RescueRequest> findByStatusOrderByCreatedAtDesc(RescueStatus status);
}
