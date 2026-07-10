-- ============================================================================
-- Rollback: report-ttl
-- Branch: feat/report-ttl                 Date: 2026-07-10
-- Idempotent — safe to run more than once. Reverses 20260710_report_ttl.up.sql
--
-- IRREVERSIBLE NOTE: dropping the columns discards any admin-set report times / custom TTLs.
-- ============================================================================
SET search_path TO baha, public;

-- Restore the view to its pre-migration column list first (it references the dropped columns).
-- DROP+CREATE so removing the two columns from the view is allowed.
DROP VIEW IF EXISTS v_active_flood_report;
CREATE VIEW v_active_flood_report AS
SELECT
    fr.id, fr.name, fr.district, fr.latitude, fr.longitude, fr.severity,
    fr.depth_cm, fr.trend, fr.road, fr.reports_count, fr.status,
    fr.reporter_user_id, u.name AS reporter_name,
    fr.created_at, fr.updated_at
FROM flood_report fr
         LEFT JOIN app_user u ON u.id = fr.reporter_user_id
WHERE fr.status = 'active';

ALTER TABLE flood_report DROP CONSTRAINT IF EXISTS flood_report_ttl_minutes_check;
ALTER TABLE flood_report DROP COLUMN IF EXISTS ttl_minutes;
ALTER TABLE flood_report DROP COLUMN IF EXISTS reported_at;

ANALYZE flood_report;
