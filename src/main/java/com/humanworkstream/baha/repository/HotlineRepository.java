package com.humanworkstream.baha.repository;

import com.humanworkstream.baha.entity.Hotline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HotlineRepository extends JpaRepository<Hotline, Integer> {
    List<Hotline> findAllByOrderByPositionAsc();
}
