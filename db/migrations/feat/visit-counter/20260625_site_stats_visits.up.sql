-- ============================================================================
-- Migration: site_stats (public all-time visit counter)
-- Branch: feat/visit-counter             Date: 2026-06-25
-- Idempotent — safe to run more than once. Rollback: 20260625_site_stats_visits.down.sql
-- ============================================================================
SET search_path TO baha, public;

-- Singleton counter row (id is pinned to 1) holding the all-time site visit tally. A single row
-- keeps increments atomic and contention-free: the API does
--   UPDATE baha.site_stats SET visits = visits + 1 WHERE id = 1 RETURNING visits;
-- to record a visit (POST /stats/visit) and SELECT visits for the read (GET /stats/visits).
CREATE TABLE IF NOT EXISTS site_stats (
    id          SMALLINT     PRIMARY KEY DEFAULT 1,
    visits      BIGINT       NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT site_stats_singleton CHECK (id = 1)
);

-- Seed the single row so the very first increment/read has a target. No-op once present.
INSERT INTO site_stats (id, visits) VALUES (1, 0)
    ON CONFLICT (id) DO NOTHING;

CREATE OR REPLACE TRIGGER trg_site_stats_updated_at
    BEFORE UPDATE ON site_stats
    FOR EACH ROW EXECUTE FUNCTION baha.fn_set_updated_at();
