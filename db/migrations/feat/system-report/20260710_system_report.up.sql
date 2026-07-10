-- ============================================================================
-- Migration: system-report (admin-logged authoritative reports)
-- Branch: feat/system-report              Date: 2026-07-10
-- Idempotent — safe to run more than once. Rollback: 20260710_system_report.down.sql
--
-- Adds a third reporter_trust tier, 'system', for flood reports an admin logs from news or other
-- sources (arbitrary location + screenshot). Widens the reporter_trust CHECK to allow it. No new
-- column: the existing flood_report.reporter_trust (added by feat/guest-reporting) carries it.
-- ============================================================================
SET search_path TO baha, public;

-- Replace the two-value CHECK with a three-value one. Drop-then-add so re-runs converge.
ALTER TABLE flood_report DROP CONSTRAINT IF EXISTS flood_report_reporter_trust_check;
DO $$ BEGIN
    ALTER TABLE flood_report ADD CONSTRAINT flood_report_reporter_trust_check
        CHECK (reporter_trust IN ('verified','guest','system'));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA baha TO baha_user;

ANALYZE flood_report;
