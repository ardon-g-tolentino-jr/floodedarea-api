package com.humanworkstream.baha.repository;

import com.humanworkstream.baha.entity.DraftIncident;
import com.humanworkstream.baha.enumeration.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DraftIncidentRepository extends JpaRepository<DraftIncident, Integer> {

    /** The review queue for a given status (newest article first). */
    List<DraftIncident> findByStatusOrderByPublishedAtDesc(IncidentStatus status);

    /** Dedupe guard for the ingestion job — one draft per source article. */
    boolean existsBySourceUrl(String sourceUrl);
}
