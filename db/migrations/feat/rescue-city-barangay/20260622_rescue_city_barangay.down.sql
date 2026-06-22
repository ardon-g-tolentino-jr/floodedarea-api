-- ============================================================================
-- Rollback: rescue_request.city + barangay (finer SOS location)
-- Branch: feat/rescue-city-barangay      Date: 2026-06-22
-- Idempotent — safe to run more than once. Reverses 20260622_rescue_city_barangay.up.sql
-- ============================================================================
SET search_path TO baha, public;

ALTER TABLE rescue_request DROP COLUMN IF EXISTS barangay;
ALTER TABLE rescue_request DROP COLUMN IF EXISTS city;
