-- ============================================================================
-- Migration: rescue_request.district (per-district rescue chart)
-- Branch: fix/rescue-district      Date: 2026-06-22
-- Idempotent — safe to run more than once. Rollback: 20260622_rescue_district.down.sql
-- ============================================================================
SET search_path TO baha, public;

-- Sender's district (reverse-geocoded client-side). Nullable; older rows stay NULL
-- and surface as "Unknown area" in the UI tally.
ALTER TABLE rescue_request
    ADD COLUMN IF NOT EXISTS district TEXT;
