-- ============================================================================
-- setup.sql — BahaWatch API: role, schema, enum types, tables, triggers,
--             indexes and views.  IDEMPOTENT — safe to run repeatedly.
-- ============================================================================
-- Schema:    baha
-- Engine:    PostgreSQL 14+ (uses CREATE OR REPLACE TRIGGER, IF NOT EXISTS)
-- Run as:    a superuser (e.g. postgres), connected to the `baha` database.
--
-- This script contains NO seed data and NO application accounts. Run order:
--   1. setup.sql          (this file — structure only)
--   2. user_creation.sql  (the platform ADMIN + demo CITIZEN accounts)
--   3. seed.sql           (hotlines, alerts, evacuation centers, sample reports)
--
-- The schema is derived from the BahaWatch UI (baha-ui): a flood-watch app for
-- Metro Manila & Rizal. Core domain objects mirror the UI:
--   flood_report      — crowd-sourced flood reports (severity / depth / trend / road)
--   evacuation_center — shelters with capacity / occupancy / contact
--   hotline           — emergency hotlines shown in the SOS modal
--   flood_alert       — rainfall / flood warning ribbon
--   rescue_request    — SOS "send rescue request with my location"
--   app_user          — self-contained auth (Basic login + Google SSO), roles
-- ============================================================================

-- ──────────────────────────────────────────────
-- Database role  (idempotent — created only if absent)
-- NOTE: the `baha` database itself must already exist and you must be
--       connected to it.
-- ──────────────────────────────────────────────
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'baha_user') THEN
        CREATE ROLE baha_user LOGIN PASSWORD 'Password123!';
    END IF;
END $$;

-- ──────────────────────────────────────────────
-- Extensions
--   pgcrypto provides crypt()/gen_salt('bf') used by user_creation.sql to
--   generate BCrypt password hashes compatible with Spring's BCryptPasswordEncoder.
-- ──────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;

-- ──────────────────────────────────────────────
-- Schema
-- ──────────────────────────────────────────────
CREATE SCHEMA IF NOT EXISTS baha;

-- ──────────────────────────────────────────────
-- Privileges  (all idempotent)
-- ──────────────────────────────────────────────
GRANT CONNECT ON DATABASE baha TO baha_user;

GRANT USAGE  ON SCHEMA baha TO baha_user;
GRANT CREATE ON SCHEMA baha TO baha_user;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES    IN SCHEMA baha TO baha_user;
GRANT USAGE, SELECT                  ON ALL SEQUENCES IN SCHEMA baha TO baha_user;

ALTER DEFAULT PRIVILEGES IN SCHEMA baha
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES    TO baha_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA baha
    GRANT USAGE, SELECT                  ON SEQUENCES TO baha_user;

SET search_path TO baha;

-- ──────────────────────────────────────────────
-- Enum types  (idempotent — CREATE TYPE has no IF NOT EXISTS)
-- ──────────────────────────────────────────────
DO $$ BEGIN CREATE TYPE severity_t      AS ENUM ('high','medium','low');                 EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE trend_t         AS ENUM ('rising','steady','receding');          EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE road_t          AS ENUM ('impassable','risky','passable');       EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE report_status_t AS ENUM ('active','resolved');                   EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE rescue_status_t AS ENUM ('pending','dispatched','resolved');     EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE alert_level_t   AS ENUM ('yellow','orange','red');               EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE user_role_t     AS ENUM ('CITIZEN','ADMIN');                     EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE auth_provider_t AS ENUM ('LOCAL','GOOGLE');                       EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- ──────────────────────────────────────────────
-- Shared trigger fn: auto-maintain updated_at
-- ──────────────────────────────────────────────
CREATE OR REPLACE FUNCTION baha.fn_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ──────────────────────────────────────────────
-- Tables  (CREATE TABLE IF NOT EXISTS keeps re-runs safe)
-- ──────────────────────────────────────────────

-- Application user — self-contained auth. password_hash is NULL for accounts
-- that only ever sign in with Google. is_new forces a first-login password set
-- for accounts provisioned with a temporary password (mirrors appointment-web).
CREATE TABLE IF NOT EXISTS app_user (
    id            INTEGER GENERATED BY DEFAULT AS IDENTITY (START WITH 100000) PRIMARY KEY,
    email         TEXT UNIQUE NOT NULL,
    password_hash TEXT,                                  -- BCrypt; NULL = Google-only
    name          TEXT NOT NULL,
    picture       TEXT,
    role          user_role_t     NOT NULL DEFAULT 'CITIZEN',
    provider      auth_provider_t NOT NULL DEFAULT 'LOCAL',
    is_new        BOOLEAN NOT NULL DEFAULT true,
    area_label    TEXT,                                  -- "monitoring area" label
    home_lat      NUMERIC(9,6),
    home_lng      NUMERIC(9,6),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE OR REPLACE TRIGGER trg_app_user_updated_at
    BEFORE UPDATE ON app_user
    FOR EACH ROW EXECUTE FUNCTION baha.fn_set_updated_at();

-- Evacuation center / shelter.
CREATE TABLE IF NOT EXISTS evacuation_center (
    id         INTEGER GENERATED BY DEFAULT AS IDENTITY (START WITH 100000) PRIMARY KEY,
    name       TEXT NOT NULL,
    latitude   NUMERIC(9,6) NOT NULL,
    longitude  NUMERIC(9,6) NOT NULL,
    capacity   INTEGER NOT NULL,
    occupancy  INTEGER NOT NULL DEFAULT 0,
    contact    TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT evacuation_center_capacity_check  CHECK (capacity >= 0),
    CONSTRAINT evacuation_center_occupancy_check CHECK (occupancy >= 0)
);

CREATE OR REPLACE TRIGGER trg_evacuation_center_updated_at
    BEFORE UPDATE ON evacuation_center
    FOR EACH ROW EXECUTE FUNCTION baha.fn_set_updated_at();

-- Emergency hotline (shown in the SOS modal).
CREATE TABLE IF NOT EXISTS hotline (
    id           INTEGER GENERATED BY DEFAULT AS IDENTITY (START WITH 100000) PRIMARY KEY,
    name         TEXT NOT NULL,
    description  TEXT,
    phone_number TEXT NOT NULL,
    position     SMALLINT NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Rainfall / flood warning ribbon.
CREATE TABLE IF NOT EXISTS flood_alert (
    id         INTEGER GENERATED BY DEFAULT AS IDENTITY (START WITH 100000) PRIMARY KEY,
    title      TEXT NOT NULL,
    body       TEXT NOT NULL,
    region     TEXT,
    level      alert_level_t NOT NULL DEFAULT 'orange',
    active     BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE OR REPLACE TRIGGER trg_flood_alert_updated_at
    BEFORE UPDATE ON flood_alert
    FOR EACH ROW EXECUTE FUNCTION baha.fn_set_updated_at();

-- Crowd-sourced flood report. reports_count mirrors the UI's "× confirmed"
-- counter; the canonical confirmations live in report_confirmation.
CREATE TABLE IF NOT EXISTS flood_report (
    id               INTEGER GENERATED BY DEFAULT AS IDENTITY (START WITH 100000) PRIMARY KEY,
    name             TEXT NOT NULL,
    district         TEXT NOT NULL,
    latitude         NUMERIC(9,6) NOT NULL,
    longitude        NUMERIC(9,6) NOT NULL,
    severity         severity_t NOT NULL,
    depth_cm         INTEGER NOT NULL,
    trend            trend_t NOT NULL DEFAULT 'steady',
    road             road_t  NOT NULL DEFAULT 'risky',
    reports_count    INTEGER NOT NULL DEFAULT 1,
    status           report_status_t NOT NULL DEFAULT 'active',
    reporter_user_id INTEGER REFERENCES app_user(id) ON DELETE SET NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT flood_report_depth_check CHECK (depth_cm >= 0)
);

CREATE OR REPLACE TRIGGER trg_flood_report_updated_at
    BEFORE UPDATE ON flood_report
    FOR EACH ROW EXECUTE FUNCTION baha.fn_set_updated_at();

-- One confirmation per user per report (drives reports_count, prevents dupes).
CREATE TABLE IF NOT EXISTS report_confirmation (
    report_id  INTEGER NOT NULL REFERENCES flood_report(id) ON DELETE CASCADE,
    user_id    INTEGER NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (report_id, user_id)
);

-- SOS — "send rescue request with my location".
CREATE TABLE IF NOT EXISTS rescue_request (
    id         INTEGER GENERATED BY DEFAULT AS IDENTITY (START WITH 100000) PRIMARY KEY,
    user_id    INTEGER REFERENCES app_user(id) ON DELETE SET NULL,
    latitude   NUMERIC(9,6) NOT NULL,
    longitude  NUMERIC(9,6) NOT NULL,
    note       TEXT,
    district   TEXT,
    city       TEXT,
    barangay   TEXT,
    status     rescue_status_t NOT NULL DEFAULT 'pending',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_requested_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE OR REPLACE TRIGGER trg_rescue_request_updated_at
    BEFORE UPDATE ON rescue_request
    FOR EACH ROW EXECUTE FUNCTION baha.fn_set_updated_at();

-- ──────────────────────────────────────────────
-- Schema-upgrade safety: add columns that arrive via later migrations.
-- No-ops on a fresh DB (CREATE TABLE above already includes them); these
-- converge an OLDER existing schema to current. All ADD COLUMN IF NOT EXISTS.
-- ──────────────────────────────────────────────
ALTER TABLE app_user     ADD COLUMN IF NOT EXISTS picture    TEXT;
ALTER TABLE app_user     ADD COLUMN IF NOT EXISTS area_label TEXT;
ALTER TABLE app_user     ADD COLUMN IF NOT EXISTS home_lat   NUMERIC(9,6);
ALTER TABLE app_user     ADD COLUMN IF NOT EXISTS home_lng   NUMERIC(9,6);
ALTER TABLE flood_report ADD COLUMN IF NOT EXISTS status     report_status_t NOT NULL DEFAULT 'active';
ALTER TABLE flood_report ADD COLUMN IF NOT EXISTS reporter_user_id INTEGER REFERENCES app_user(id) ON DELETE SET NULL;
ALTER TABLE rescue_request ADD COLUMN IF NOT EXISTS last_requested_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE rescue_request ADD COLUMN IF NOT EXISTS district TEXT;
ALTER TABLE rescue_request ADD COLUMN IF NOT EXISTS city     TEXT;
ALTER TABLE rescue_request ADD COLUMN IF NOT EXISTS barangay TEXT;

-- ──────────────────────────────────────────────
-- Indexes — foreign keys & hot paths (all IF NOT EXISTS)
-- ──────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_flood_report_status        ON flood_report(status);
CREATE INDEX IF NOT EXISTS idx_flood_report_severity      ON flood_report(severity);
CREATE INDEX IF NOT EXISTS idx_flood_report_created_at    ON flood_report(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_flood_report_reporter      ON flood_report(reporter_user_id);
CREATE INDEX IF NOT EXISTS idx_flood_report_active        ON flood_report(created_at DESC) WHERE status = 'active';

CREATE INDEX IF NOT EXISTS idx_report_confirmation_user   ON report_confirmation(user_id);

CREATE INDEX IF NOT EXISTS idx_rescue_request_status      ON rescue_request(status);
CREATE INDEX IF NOT EXISTS idx_rescue_request_user        ON rescue_request(user_id);
CREATE INDEX IF NOT EXISTS idx_rescue_request_user_status ON rescue_request(user_id, status);
CREATE INDEX IF NOT EXISTS idx_rescue_request_created_at  ON rescue_request(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_flood_alert_active         ON flood_alert(active) WHERE active = true;
CREATE INDEX IF NOT EXISTS idx_hotline_position           ON hotline(position);

-- ──────────────────────────────────────────────
-- Views  (CREATE OR REPLACE VIEW — idempotent)
-- ──────────────────────────────────────────────

-- Active reports joined with reporter display info (read model for the map/feed).
CREATE OR REPLACE VIEW v_active_flood_report AS
SELECT
    fr.id, fr.name, fr.district, fr.latitude, fr.longitude, fr.severity,
    fr.depth_cm, fr.trend, fr.road, fr.reports_count, fr.status,
    fr.reporter_user_id, u.name AS reporter_name,
    fr.created_at, fr.updated_at
FROM flood_report fr
         LEFT JOIN app_user u ON u.id = fr.reporter_user_id
WHERE fr.status = 'active';

-- Evacuation center with derived occupancy percentage / spots left / status label.
CREATE OR REPLACE VIEW v_evacuation_center_status AS
SELECT
    c.id, c.name, c.latitude, c.longitude, c.capacity, c.occupancy, c.contact,
    CASE WHEN c.capacity > 0 THEN ROUND(c.occupancy::numeric / c.capacity * 100) ELSE 0 END AS pct_full,
    GREATEST(0, c.capacity - c.occupancy) AS spots_left,
    CASE
        WHEN c.capacity > 0 AND c.occupancy >= c.capacity                   THEN 'full'
        WHEN c.capacity > 0 AND c.occupancy::numeric / c.capacity >= 0.85   THEN 'filling'
        ELSE 'open'
    END AS occupancy_status,
    c.created_at, c.updated_at
FROM evacuation_center c;

-- ──────────────────────────────────────────────
-- Re-grant on objects created in this run (covers any pre-existing tables
-- that ALTER DEFAULT PRIVILEGES would not have reached).
-- ──────────────────────────────────────────────
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES    IN SCHEMA baha TO baha_user;
GRANT USAGE, SELECT                  ON ALL SEQUENCES IN SCHEMA baha TO baha_user;
GRANT SELECT                         ON ALL TABLES    IN SCHEMA baha TO baha_user;

-- ──────────────────────────────────────────────
-- feat/guest-reporting — no-login guest accounts + report author trust.
--   Appended (idempotent). See db/migrations/feat/guest-reporting/.
-- ──────────────────────────────────────────────
-- GUEST role for no-login device sessions (report + confirm only; no SOS/feedback).
ALTER TYPE user_role_t ADD VALUE IF NOT EXISTS 'GUEST';

-- Stable per-device id for guest accounts; NULL for real (email) accounts. A plain unique index
-- still allows many NULLs (Postgres treats NULLs as distinct).
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS device_id TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS uq_app_user_device_id ON app_user(device_id);

-- Denormalized author trust per report: 'verified' (real account) or 'guest' (no-login).
ALTER TABLE flood_report ADD COLUMN IF NOT EXISTS reporter_trust TEXT NOT NULL DEFAULT 'verified';
DO $$ BEGIN
    ALTER TABLE flood_report ADD CONSTRAINT flood_report_reporter_trust_check
        CHECK (reporter_trust IN ('verified','guest'));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA baha TO baha_user;

-- ──────────────────────────────────────────────
-- feat/feedback + feat/feedback-upvotes — community bug reports / feature requests and upvoting.
--   Appended (idempotent). The feedback table was introduced via migration only; it is included
--   here so a fresh setup.sql build is self-contained. See db/migrations/feat/feedback*/.
-- ──────────────────────────────────────────────
DO $$ BEGIN CREATE TYPE feedback_type_t   AS ENUM ('bug','feature');                 EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN CREATE TYPE feedback_status_t AS ENUM ('pending','accepted','declined'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- A bug report or feature request. Anyone (account or no-login guest) can submit; admins
-- accept/decline; accepted items appear on the public board. submitter_name snapshots the author.
CREATE TABLE IF NOT EXISTS feedback (
    id                   INTEGER GENERATED BY DEFAULT AS IDENTITY (START WITH 1000) PRIMARY KEY,
    type                 feedback_type_t   NOT NULL,
    title                TEXT NOT NULL,
    details              TEXT,
    status               feedback_status_t NOT NULL DEFAULT 'pending',
    submitted_by_user_id INTEGER REFERENCES app_user(id) ON DELETE SET NULL,
    submitter_name       TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_feedback_type_status ON feedback(type, status);
CREATE OR REPLACE TRIGGER trg_feedback_updated_at
    BEFORE UPDATE ON feedback
    FOR EACH ROW EXECUTE FUNCTION baha.fn_set_updated_at();

-- One row per (feedback, voter); voter = app_user id (guests included). Re-voting toggles the row.
CREATE TABLE IF NOT EXISTS feedback_upvote (
    id          INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    feedback_id INTEGER NOT NULL REFERENCES feedback(id) ON DELETE CASCADE,
    user_id     INTEGER NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_feedback_upvote UNIQUE (feedback_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_feedback_upvote_user ON feedback_upvote(user_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES    IN SCHEMA baha TO baha_user;
GRANT USAGE, SELECT                  ON ALL SEQUENCES IN SCHEMA baha TO baha_user;

-- ──────────────────────────────────────────────
-- feat/report-photo — optional flood-report photo stored in the DB (served via /reports/:id/photo).
--   Appended (idempotent). See db/migrations/feat/report-photo/.
--   The bytes live in flood_report.photo (BYTEA, auto-TOASTed); photo_mime flags presence and
--   type. The API never returns the bytes in list/detail JSON — only a derived photoUrl.
-- ──────────────────────────────────────────────
ALTER TABLE flood_report ADD COLUMN IF NOT EXISTS photo      BYTEA;
ALTER TABLE flood_report ADD COLUMN IF NOT EXISTS photo_mime TEXT;
DO $$ BEGIN
    ALTER TABLE flood_report ADD CONSTRAINT flood_report_photo_mime_check
        CHECK (photo_mime IS NULL OR photo_mime IN ('image/jpeg','image/png','image/webp'));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
    ALTER TABLE flood_report ADD CONSTRAINT flood_report_photo_pairing_check
        CHECK ((photo IS NULL) = (photo_mime IS NULL));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES    IN SCHEMA baha TO baha_user;
GRANT USAGE, SELECT                  ON ALL SEQUENCES IN SCHEMA baha TO baha_user;

-- ──────────────────────────────────────────────
-- feat/system-report — admin-logged authoritative reports (news/other sources). Adds a third
--   reporter_trust tier, 'system'. Appended (idempotent). See db/migrations/feat/system-report/.
--   Widens the reporter_trust CHECK (added two-value by feat/guest-reporting above).
-- ──────────────────────────────────────────────
ALTER TABLE flood_report DROP CONSTRAINT IF EXISTS flood_report_reporter_trust_check;
DO $$ BEGIN
    ALTER TABLE flood_report ADD CONSTRAINT flood_report_reporter_trust_check
        CHECK (reporter_trust IN ('verified','guest','system'));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES    IN SCHEMA baha TO baha_user;
GRANT USAGE, SELECT                  ON ALL SEQUENCES IN SCHEMA baha TO baha_user;

-- ──────────────────────────────────────────────
-- feat/report-ttl — admin-defined lifetime + source report time for system reports. Appended
--   (idempotent). See db/migrations/feat/report-ttl/. The client anchors its freshness countdown
--   on reported_at and uses ttl_minutes as a per-report override of the severity default.
-- ──────────────────────────────────────────────
ALTER TABLE flood_report ADD COLUMN IF NOT EXISTS reported_at TIMESTAMPTZ;
ALTER TABLE flood_report ADD COLUMN IF NOT EXISTS ttl_minutes INTEGER;
DO $$ BEGIN
    ALTER TABLE flood_report ADD CONSTRAINT flood_report_ttl_minutes_check
        CHECK (ttl_minutes IS NULL OR ttl_minutes > 0);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- DROP+CREATE (not CREATE OR REPLACE): converging an existing DB whose view lacks these columns
-- changes the column order, which CREATE OR REPLACE rejects. No object depends on this view.
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

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES    IN SCHEMA baha TO baha_user;
GRANT USAGE, SELECT                  ON ALL SEQUENCES IN SCHEMA baha TO baha_user;
GRANT SELECT ON v_active_flood_report TO baha_user;

-- ──────────────────────────────────────────────
-- feat/news-incidents — admin review queue for flood incidents drafted from news. Appended
--   (idempotent). See db/migrations/feat/news-incidents/. The ingestion job files pending drafts;
--   an admin approves (→ a system flood_report) or dismisses. Reuses severity_t/trend_t/road_t.
-- ──────────────────────────────────────────────
DO $$ BEGIN CREATE TYPE incident_status_t AS ENUM ('pending','approved','dismissed'); EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS draft_incident (
    id                INTEGER GENERATED BY DEFAULT AS IDENTITY (START WITH 100000) PRIMARY KEY,
    headline          TEXT NOT NULL,
    source_name       TEXT NOT NULL,
    source_url        TEXT NOT NULL,
    published_at      TIMESTAMPTZ NOT NULL,
    name              TEXT NOT NULL,
    district          TEXT NOT NULL,
    latitude          NUMERIC(9,6) NOT NULL,
    longitude         NUMERIC(9,6) NOT NULL,
    severity          severity_t NOT NULL,
    depth_cm          INTEGER NOT NULL,
    trend             trend_t NOT NULL DEFAULT 'steady',
    road              road_t  NOT NULL DEFAULT 'risky',
    confidence        DOUBLE PRECISION NOT NULL DEFAULT 0,
    status            incident_status_t NOT NULL DEFAULT 'pending',
    created_report_id INTEGER REFERENCES flood_report(id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT draft_incident_depth_check      CHECK (depth_cm >= 0),
    CONSTRAINT draft_incident_confidence_check CHECK (confidence >= 0 AND confidence <= 1),
    CONSTRAINT draft_incident_source_url_key   UNIQUE (source_url)
);
CREATE OR REPLACE TRIGGER trg_draft_incident_updated_at
    BEFORE UPDATE ON draft_incident
    FOR EACH ROW EXECUTE FUNCTION baha.fn_set_updated_at();
CREATE INDEX IF NOT EXISTS idx_draft_incident_status_published ON draft_incident(status, published_at DESC);
CREATE INDEX IF NOT EXISTS idx_draft_incident_created_report   ON draft_incident(created_report_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES    IN SCHEMA baha TO baha_user;
GRANT USAGE, SELECT                  ON ALL SEQUENCES IN SCHEMA baha TO baha_user;
