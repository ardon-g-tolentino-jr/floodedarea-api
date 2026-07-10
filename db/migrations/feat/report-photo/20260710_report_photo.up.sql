-- ============================================================================
-- Migration: report-photo (optional flood-report photo, stored in the DB)
-- Branch: feat/report-photo               Date: 2026-07-10
-- Idempotent — safe to run more than once. Rollback: 20260710_report_photo.down.sql
--
-- Adds an OPTIONAL photo to a flood report. The image bytes live in the DB (photo BYTEA) with
-- their MIME type (photo_mime). Postgres automatically TOASTs the bytea out of the main heap, so
-- the column does not bloat the hot-path row scans used by the map/feed.
--
-- The reports list/detail JSON never carries the bytes: the API maps only photo_mime (a tiny
-- TEXT flag) on the entity and derives a photoUrl from it, streaming the image separately from
-- the public GET /reports/:id/photo endpoint.
-- ============================================================================
SET search_path TO baha, public;

ALTER TABLE flood_report ADD COLUMN IF NOT EXISTS photo      BYTEA;
ALTER TABLE flood_report ADD COLUMN IF NOT EXISTS photo_mime TEXT;

-- Only real image types are ever stored (also enforced in the app). Guard at the DB too.
DO $$ BEGIN
    ALTER TABLE flood_report ADD CONSTRAINT flood_report_photo_mime_check
        CHECK (photo_mime IS NULL OR photo_mime IN ('image/jpeg','image/png','image/webp'));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- photo and photo_mime are always written/cleared together (the app writes both in one statement).
DO $$ BEGIN
    ALTER TABLE flood_report ADD CONSTRAINT flood_report_photo_pairing_check
        CHECK ((photo IS NULL) = (photo_mime IS NULL));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- No index: the photo is never filtered/joined/ordered — it is fetched only by primary key.

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA baha TO baha_user;

ANALYZE flood_report;
