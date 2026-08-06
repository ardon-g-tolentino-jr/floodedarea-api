-- ============================================================================
-- Rollback: social-auto-post
-- Branch: feat/social-auto-post              Date: 2026-08-06
-- Idempotent — safe to run more than once. Reverses 20260806_social_auto_post.up.sql
--
-- IRREVERSIBLE NOTE: dropping the table discards the full history of Facebook/X publish attempts
-- (status, external ids/urls, errors) for every report. The two app_setting rows are also removed.
-- ============================================================================
SET search_path TO baha, public;

DROP TABLE IF EXISTS social_post;

DELETE FROM app_setting WHERE key IN ('social_autopost_facebook', 'social_autopost_x');
