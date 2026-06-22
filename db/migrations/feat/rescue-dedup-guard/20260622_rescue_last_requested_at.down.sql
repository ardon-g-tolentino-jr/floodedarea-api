-- ============================================================================
-- Rollback: rescue_request.last_requested_at (1-hour SOS dedup guard)
-- Branch: feat/rescue-dedup-guard      Date: 2026-06-22
-- Idempotent — safe to run more than once. Reverses 20260622_rescue_last_requested_at.up.sql
-- ============================================================================
SET search_path TO baha, public;

DROP INDEX IF EXISTS idx_rescue_request_user_status;

ALTER TABLE rescue_request
    DROP COLUMN IF EXISTS last_requested_at;
