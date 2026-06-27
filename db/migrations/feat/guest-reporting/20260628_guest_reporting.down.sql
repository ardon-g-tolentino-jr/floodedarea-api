-- ============================================================================
-- Rollback: guest-reporting
-- Branch: feat/guest-reporting           Date: 2026-06-28
-- Idempotent — safe to run more than once. Reverses 20260628_guest_reporting.up.sql
--
-- IRREVERSIBLE NOTE: PostgreSQL cannot remove a value from an enum type, so the 'GUEST' label
-- on user_role_t REMAINS after rollback (harmless once guest rows are gone). The DELETE below
-- discards all guest accounts; their reports' reporter_user_id is set NULL (ON DELETE SET NULL).
-- ============================================================================
SET search_path TO baha, public;

-- Remove guest accounts (data loss — reports become unattributed, not deleted).
DELETE FROM app_user WHERE role = 'GUEST';

-- Drop report author-trust tagging.
ALTER TABLE flood_report DROP CONSTRAINT IF EXISTS flood_report_reporter_trust_check;
ALTER TABLE flood_report DROP COLUMN IF EXISTS reporter_trust;

-- Drop the guest device link.
DROP INDEX IF EXISTS uq_app_user_device_id;
ALTER TABLE app_user DROP COLUMN IF EXISTS device_id;

-- user_role_t still contains 'GUEST' — enum values cannot be dropped in PostgreSQL.
