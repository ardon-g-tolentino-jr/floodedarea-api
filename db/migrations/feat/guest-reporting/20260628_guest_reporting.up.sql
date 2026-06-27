-- ============================================================================
-- Migration: guest-reporting (no-login guest accounts + report author trust)
-- Branch: feat/guest-reporting           Date: 2026-06-28
-- Idempotent — safe to run more than once. Rollback: 20260628_guest_reporting.down.sql
--
-- Adds a GUEST role so people can report floods without an account, a per-device id to key
-- guest accounts (keeps the "one active report per person" spam guard working), and a
-- reporter_trust tag so the UI can render guest reports as provisional/unverified.
-- ============================================================================
SET search_path TO baha, public;

-- GUEST role for no-login device sessions (report + confirm only; not SOS/feedback).
-- NOTE: ALTER TYPE ... ADD VALUE must run outside an explicit transaction block; psql -f runs
-- each statement in its own autocommit, so this file must NOT be wrapped in BEGIN/COMMIT.
ALTER TYPE user_role_t ADD VALUE IF NOT EXISTS 'GUEST';

-- Stable per-device id for guest accounts; NULL for real (email) accounts. A plain unique index
-- still allows many NULLs (Postgres treats NULLs as distinct).
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS device_id TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS uq_app_user_device_id ON app_user(device_id);

-- Denormalized author trust per report: 'verified' (real account) or 'guest' (no-login).
ALTER TABLE flood_report ADD COLUMN IF NOT EXISTS reporter_trust TEXT NOT NULL DEFAULT 'verified';
DO $$ BEGIN
    ALTER TABLE flood_report ADD CONSTRAINT flood_report_reporter_trust_check
        CHECK (reporter_trust IN ('verified','guest'));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA baha TO baha_user;

ANALYZE app_user;
ANALYZE flood_report;
