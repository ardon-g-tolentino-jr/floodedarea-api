-- ============================================================================
-- Migration: report-ttl (admin-defined lifetime + source report time)
-- Branch: feat/report-ttl                 Date: 2026-07-10
-- Idempotent — safe to run more than once. Rollback: 20260710_report_ttl.down.sql
--
-- For admin "system" reports (news/other sources), the admin can record WHEN the flood was
-- reported by the source (reported_at) and HOW LONG the report should stay live (ttl_minutes).
-- The client anchors its freshness countdown on reported_at and uses ttl_minutes as a per-report
-- override of the severity-scaled default. Both are nullable; citizen reports leave them unset and
-- keep their existing behavior.
-- ============================================================================
SET search_path TO baha, public;

ALTER TABLE flood_report ADD COLUMN IF NOT EXISTS reported_at TIMESTAMPTZ;
ALTER TABLE flood_report ADD COLUMN IF NOT EXISTS ttl_minutes INTEGER;

DO $$ BEGIN
    ALTER TABLE flood_report ADD CONSTRAINT flood_report_ttl_minutes_check
        CHECK (ttl_minutes IS NULL OR ttl_minutes > 0);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- Surface the two fields in the active-reports read model. DROP+CREATE (not CREATE OR REPLACE)
-- because inserting columns changes the view's column order, which CREATE OR REPLACE rejects.
-- Safe: no Java code or other DB object depends on this view.
DROP VIEW IF EXISTS v_active_flood_report;
CREATE VIEW v_active_flood_report AS
SELECT
    fr.id, fr.name, fr.district, fr.latitude, fr.longitude, fr.severity,
    fr.depth_cm, fr.trend, fr.road, fr.reports_count, fr.status,
    fr.reporter_user_id, u.name AS reporter_name,
    fr.reported_at, fr.ttl_minutes,
    fr.created_at, fr.updated_at
FROM flood_report fr
         LEFT JOIN app_user u ON u.id = fr.reporter_user_id
WHERE fr.status = 'active';

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA baha TO baha_user;
GRANT SELECT ON v_active_flood_report TO baha_user;

ANALYZE flood_report;
