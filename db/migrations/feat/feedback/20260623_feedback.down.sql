-- ============================================================================
-- Rollback: feedback (user bug reports & feature requests)
-- Branch: feat/feedback                  Date: 2026-06-23
-- Idempotent — safe to run more than once. Reverses 20260623_feedback.up.sql
-- ============================================================================
SET search_path TO baha, public;

DROP TABLE IF EXISTS feedback;
DROP TYPE IF EXISTS feedback_status_t;
DROP TYPE IF EXISTS feedback_type_t;
