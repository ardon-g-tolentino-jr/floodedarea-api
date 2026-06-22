-- ============================================================================
-- Migration: rescue_request.city + barangay (finer SOS location)
-- Branch: feat/rescue-city-barangay      Date: 2026-06-22
-- Idempotent — safe to run more than once. Rollback: 20260622_rescue_city_barangay.down.sql
-- ============================================================================
SET search_path TO baha, public;

-- Sender's city/municipality and barangay (reverse-geocoded client-side). Both nullable;
-- older rows stay NULL.
ALTER TABLE rescue_request ADD COLUMN IF NOT EXISTS city     TEXT;
ALTER TABLE rescue_request ADD COLUMN IF NOT EXISTS barangay TEXT;
