package com.humanworkstream.baha.service;

import com.humanworkstream.baha.entity.SiteStats;
import com.humanworkstream.baha.repository.SiteStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SiteStatsService {

    /** The pinned id of the singleton counter row (see the site_stats migration). */
    private static final short ROW_ID = 1;

    private final SiteStatsRepository repository;

    /** Current all-time visit total (0 if the row is somehow absent). */
    @Transactional(readOnly = true)
    public long total() {
        return repository.findById(ROW_ID).map(SiteStats::getVisits).orElse(0L);
    }

    /**
     * Record one visit and return the new all-time total. The singleton row is locked for the
     * read-modify-write so concurrent visits can't lose an increment. Self-heals by seeding the
     * row if the migration's seed is somehow missing.
     */
    @Transactional
    public long recordVisit() {
        SiteStats stats = repository.findForUpdate().orElseGet(() -> {
            SiteStats fresh = new SiteStats();
            fresh.setId(ROW_ID);
            fresh.setVisits(0L);
            return fresh;
        });
        stats.setVisits(stats.getVisits() + 1);
        return repository.save(stats).getVisits();
    }
}
