-- ============================================================================
-- Rollback: predictive-water-level
-- Branch: 077-feat-predictive-water-level   Date: 2026-08-14
-- Idempotent — safe to run more than once. Reverses 20260814_predictive_water_level.up.sql
--
-- IRREVERSIBLE NOTE: deleting the row discards the admin's current rain_active choice; the app
-- falls back to isEnabled("rain_active", false), i.e. OFF, until an admin sets it again.
-- ============================================================================
SET search_path TO baha, public;

DELETE FROM app_setting WHERE key = 'rain_active';
