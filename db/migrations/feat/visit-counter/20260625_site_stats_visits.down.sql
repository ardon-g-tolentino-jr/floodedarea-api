-- ============================================================================
-- Rollback: site_stats (public all-time visit counter)
-- Branch: feat/visit-counter             Date: 2026-06-25
-- Idempotent — safe to run more than once. Reverses 20260625_site_stats_visits.up.sql
-- ============================================================================
SET search_path TO baha, public;

-- Dropping the table also removes trg_site_stats_updated_at. The shared
-- baha.fn_set_updated_at() function is left in place (used by other tables).
DROP TABLE IF EXISTS site_stats;
