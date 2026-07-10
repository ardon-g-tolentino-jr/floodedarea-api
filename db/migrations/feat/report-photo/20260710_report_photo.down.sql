-- ============================================================================
-- Rollback: report-photo
-- Branch: feat/report-photo               Date: 2026-07-10
-- Idempotent — safe to run more than once. Reverses 20260710_report_photo.up.sql
--
-- IRREVERSIBLE NOTE: dropping these columns permanently discards every stored flood photo.
-- ============================================================================
SET search_path TO baha, public;

ALTER TABLE flood_report DROP CONSTRAINT IF EXISTS flood_report_photo_pairing_check;
ALTER TABLE flood_report DROP CONSTRAINT IF EXISTS flood_report_photo_mime_check;
ALTER TABLE flood_report DROP COLUMN IF EXISTS photo_mime;
ALTER TABLE flood_report DROP COLUMN IF EXISTS photo;

ANALYZE flood_report;
