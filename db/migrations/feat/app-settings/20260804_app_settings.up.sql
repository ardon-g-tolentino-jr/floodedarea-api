-- ============================================================================
-- Migration: app-settings (admin-controlled global app settings)
-- Branch: 050-feat-quick-report-admin-polish   Date: 2026-08-04
-- Idempotent — safe to run more than once. Rollback: 20260804_app_settings.down.sql
--
-- A single global key/value settings table so an admin can toggle app-wide behavior (starting
-- with the community chat widget) without a deploy. GET /settings is public; PATCH is ADMIN-only.
-- ============================================================================
SET search_path TO baha, public;

CREATE TABLE IF NOT EXISTS app_setting (
    key        TEXT PRIMARY KEY,
    value      TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO app_setting (key, value) VALUES ('chat_enabled', 'true')
    ON CONFLICT (key) DO NOTHING;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES    IN SCHEMA baha TO baha_user;
GRANT USAGE, SELECT                  ON ALL SEQUENCES IN SCHEMA baha TO baha_user;

ANALYZE app_setting;
