-- ============================================================================
-- Rollback: flood-prone-zones
-- Branch: 054-feat-flood-prone-zones   Date: 2026-08-05
-- Idempotent — safe to run more than once. Reverses 20260805_flood_prone_zones.up.sql
--
-- IRREVERSIBLE NOTE: dropping flood_prone_area discards all curated zones, and dropping the
-- flood_alert geometry columns de-targets any past warnings (they become plain global alerts).
-- ============================================================================
SET search_path TO baha, public;

DROP INDEX IF EXISTS idx_flood_alert_area_id;
ALTER TABLE flood_alert DROP COLUMN IF EXISTS area_id;
ALTER TABLE flood_alert DROP COLUMN IF EXISTS radius_m;
ALTER TABLE flood_alert DROP COLUMN IF EXISTS longitude;
ALTER TABLE flood_alert DROP COLUMN IF EXISTS latitude;

DROP TABLE IF EXISTS flood_prone_area;
