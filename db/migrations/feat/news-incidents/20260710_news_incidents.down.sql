-- ============================================================================
-- Rollback: news-incidents
-- Branch: feat/news-incidents             Date: 2026-07-10
-- Idempotent — safe to run more than once. Reverses 20260710_news_incidents.up.sql
--
-- IRREVERSIBLE NOTE: drops the draft_incident queue and all pending/approved/dismissed drafts.
-- Approved drafts have already become independent flood_report rows — those are NOT removed here
-- (created_report_id was ON DELETE SET NULL), so no live report is lost by this rollback.
-- ============================================================================
SET search_path TO baha, public;

DROP TABLE IF EXISTS draft_incident;

-- Drop the enum only after its sole table is gone.
DROP TYPE IF EXISTS incident_status_t;
