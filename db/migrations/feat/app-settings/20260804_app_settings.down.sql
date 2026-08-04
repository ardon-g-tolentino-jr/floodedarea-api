-- ============================================================================
-- Rollback: app-settings
-- Branch: 050-feat-quick-report-admin-polish   Date: 2026-08-04
-- Idempotent — safe to run more than once. Reverses 20260804_app_settings.up.sql
--
-- IRREVERSIBLE NOTE: dropping the table discards any admin-set app settings (e.g. chat_enabled).
-- ============================================================================
SET search_path TO baha, public;

DROP TABLE IF EXISTS app_setting;
