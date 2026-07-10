-- ============================================================================
-- Rollback: system-report
-- Branch: feat/system-report              Date: 2026-07-10
-- Idempotent — safe to run more than once. Reverses 20260710_system_report.up.sql
--
-- IRREVERSIBLE NOTE: any existing rows with reporter_trust='system' must be reclassified first,
-- or the restored two-value CHECK will fail to validate. Reclassify them to 'verified' below.
-- ============================================================================
SET search_path TO baha, public;

-- Existing system reports become 'verified' so the two-value CHECK can be restored.
UPDATE flood_report SET reporter_trust = 'verified' WHERE reporter_trust = 'system';

ALTER TABLE flood_report DROP CONSTRAINT IF EXISTS flood_report_reporter_trust_check;
DO $$ BEGIN
    ALTER TABLE flood_report ADD CONSTRAINT flood_report_reporter_trust_check
        CHECK (reporter_trust IN ('verified','guest'));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

ANALYZE flood_report;
