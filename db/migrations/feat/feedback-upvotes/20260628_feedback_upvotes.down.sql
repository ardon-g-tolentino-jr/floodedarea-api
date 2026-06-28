-- ============================================================================
-- Rollback: feedback-upvotes (community upvoting of accepted feedback)
-- Branch: feat/feedback-upvotes          Date: 2026-06-28
-- Idempotent — safe to run more than once. Reverses 20260628_feedback_upvotes.up.sql
--
-- DATA LOSS: dropping feedback_upvote discards all recorded upvotes.
-- ============================================================================
SET search_path TO baha, public;

DROP TABLE IF EXISTS feedback_upvote;
