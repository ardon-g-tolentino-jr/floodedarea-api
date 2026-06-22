-- ============================================================================
-- user_creation.sql — provision the application accounts (Basic-login users).
--                     IDEMPOTENT — safe to run repeatedly.
-- ============================================================================
-- Run order: setup.sql → user_creation.sql → seed.sql
-- Run as a superuser or the application role (baha_user), connected to the
-- `baha` database. Requires that setup.sql has already run (it enables the
-- pgcrypto extension used here to hash passwords).
--
-- WHAT THIS DOES
--   Creates two self-contained login accounts for the BahaWatch API:
--     • ADMIN   admin@bahawatch.ph     password: Admin123!
--     • CITIZEN citizen@bahawatch.ph   password: Citizen123!
--   ADMINs can manage evacuation centers, hotlines, alerts, resolve reports,
--   and view/dispatch rescue requests. CITIZENs can report floods, confirm
--   reports, and send SOS rescue requests.
--
-- PASSWORD HASHING
--   Passwords are hashed with BCrypt via pgcrypto's crypt()/gen_salt('bf'),
--   producing a `$2a$` hash that Spring Security's BCryptPasswordEncoder
--   verifies natively. Change these credentials before any real deployment.
--
--   Google-SSO-only users are created automatically on first sign-in by the
--   API (provider = GOOGLE, password_hash = NULL) and are NOT seeded here.
--
--   is_new = false so these accounts log in directly without the first-login
--   "set a new password" flow.
-- ============================================================================

-- Include `public` so pgcrypto's crypt()/gen_salt() (installed there by
-- setup.sql) are resolvable; `baha` stays first for the app tables.
SET search_path TO baha, public;

-- Platform ADMIN — insert if absent (idempotent).
INSERT INTO app_user (email, password_hash, name, role, provider, is_new)
SELECT 'admin@bahawatch.ph',
       crypt('Admin123!', gen_salt('bf')),
       'BahaWatch Admin',
       'ADMIN',
       'LOCAL',
       false
WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE email = 'admin@bahawatch.ph');

-- Promote an existing row to ADMIN if the email was already present
-- (e.g. previously signed up as a CITIZEN). Idempotent.
UPDATE app_user
   SET role = 'ADMIN'
 WHERE email = 'admin@bahawatch.ph'
   AND role <> 'ADMIN';

-- Demo CITIZEN — insert if absent (idempotent).
INSERT INTO app_user (email, password_hash, name, role, provider, is_new, area_label, home_lat, home_lng)
SELECT 'citizen@bahawatch.ph',
       crypt('Citizen123!', gen_salt('bf')),
       'Juan Dela Cruz',
       'CITIZEN',
       'LOCAL',
       false,
       'Marikina Heights',
       14.660000,
       121.120000
WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE email = 'citizen@bahawatch.ph');
