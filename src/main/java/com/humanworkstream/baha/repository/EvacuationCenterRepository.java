package com.humanworkstream.baha.repository;

import com.humanworkstream.baha.entity.EvacuationCenter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvacuationCenterRepository extends JpaRepository<EvacuationCenter, Integer> {
    List<EvacuationCenter> findAllByOrderByNameAsc();
}
