-- ============================================================================
-- Migration: rescue_request.last_requested_at (1-hour SOS dedup guard)
-- Branch: feat/rescue-dedup-guard      Date: 2026-06-22
-- Idempotent — safe to run more than once. Rollback: 20260622_rescue_last_requested_at.down.sql
-- ============================================================================
SET search_path TO baha, public;

-- Column anchoring the per-user dedup window (refreshed on each repeat SOS).
ALTER TABLE rescue_request
    ADD COLUMN IF NOT EXISTS last_requested_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- Backfill any pre-existing rows to their creation time (no-op on a fresh column
-- since the DEFAULT already populated them; explicit for older partial states).
UPDATE rescue_request
    SET last_requested_at = created_at
    WHERE last_requested_at IS NULL;

-- Hot path for the dedup lookup (sender's most recent active request).
CREATE INDEX IF NOT EXISTS idx_rescue_request_user_status
    ON rescue_request(user_id, status);
