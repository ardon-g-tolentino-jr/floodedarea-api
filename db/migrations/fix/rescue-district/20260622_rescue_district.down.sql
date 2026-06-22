-- ============================================================================
-- Rollback: rescue_request.district (per-district rescue chart)
-- Branch: fix/rescue-district      Date: 2026-06-22
-- Idempotent — safe to run more than once. Reverses 20260622_rescue_district.up.sql
-- ============================================================================
SET search_path TO baha, public;

ALTER TABLE rescue_request
    DROP COLUMN IF EXISTS district;
