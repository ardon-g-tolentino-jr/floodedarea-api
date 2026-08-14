-- ============================================================================
-- Migration: predictive-water-level (admin "rain is falling now" toggle)
-- Branch: 077-feat-predictive-water-level   Date: 2026-08-14
-- Idempotent — safe to run more than once. Rollback: 20260814_predictive_water_level.down.sql
--
-- No schema change — app_setting already exists (see db/migrations/feat/app-settings/). This
-- seeds the one new key/value row the frontend's predictive water-level model reads: a global
-- flag flipping the model's projection direction from receding (default) to rising while rain is
-- falling. Defaults OFF, unlike the other app_setting flags, which default ON when missing.
-- ============================================================================
SET search_path TO baha, public;

INSERT INTO app_setting (key, value) VALUES ('rain_active', 'false')
    ON CONFLICT (key) DO NOTHING;

ANALYZE app_setting;
