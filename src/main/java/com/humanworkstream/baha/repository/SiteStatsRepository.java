package com.humanworkstream.baha.repository;

import com.humanworkstream.baha.entity.SiteStats;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface SiteStatsRepository extends JpaRepository<SiteStats, Short> {

    /** Lock the singleton row for a read-modify-write increment, so concurrent visits serialize
     *  and none is lost. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SiteStats s WHERE s.id = 1")
    Optional<SiteStats> findForUpdate();
}
