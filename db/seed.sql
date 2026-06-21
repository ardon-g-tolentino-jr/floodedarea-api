-- ============================================================================
-- seed.sql — BahaWatch reference / demo data.
--            IDEMPOTENT — safe to run repeatedly; never clobbers edited values.
-- ============================================================================
-- Run order: setup.sql → user_creation.sql → seed.sql
-- Run as a superuser or the application role (baha_user), connected to the
-- `baha` database. Requires that setup.sql has already run.
--
-- Contents (mirrors the BahaWatch UI demo state):
--   1. hotline           — emergency hotlines (SOS modal)
--   2. flood_alert        — active rainfall warning ribbon
--   3. evacuation_center  — shelters
--   4. flood_report       — sample active flood reports
--
-- Coordinates are real lat/lng for Metro Manila & Rizal so the Leaflet map
-- plots them correctly (the original design used abstract x/y canvas points).
-- ============================================================================

SET search_path TO baha;

-- ============================================================================
-- 1. HOTLINE — emergency numbers. Keyed by phone_number for idempotency.
-- ============================================================================
INSERT INTO hotline (name, description, phone_number, position)
SELECT v.name, v.description, v.phone_number, v.position
FROM (VALUES
  ('National Emergency',   'Police · Fire · Rescue', '911',            0),
  ('NDRRMC',               'Disaster response',      '(02) 8911-1406', 1),
  ('Marikina Rescue 161',  'Local flood rescue',     '161',            2),
  ('Philippine Red Cross', 'Medical & relief',       '143',            3)
) AS v(name, description, phone_number, position)
WHERE NOT EXISTS (SELECT 1 FROM hotline h WHERE h.phone_number = v.phone_number);

-- ============================================================================
-- 2. FLOOD_ALERT — the rainfall-warning ribbon. Keyed by title for idempotency.
-- ============================================================================
INSERT INTO flood_alert (title, body, region, level, active)
SELECT 'Orange Rainfall Warning',
       'In effect for eastern Metro Manila & Rizal — flooding likely in low-lying and riverside areas. Avoid the Marikina River basin.',
       'Metro Manila & Rizal',
       'orange',
       true
WHERE NOT EXISTS (SELECT 1 FROM flood_alert a WHERE a.title = 'Orange Rainfall Warning');

-- ============================================================================
-- 3. EVACUATION_CENTER — shelters. Keyed by name for idempotency.
-- ============================================================================
INSERT INTO evacuation_center (name, latitude, longitude, capacity, occupancy, contact)
SELECT v.name, v.latitude, v.longitude, v.capacity, v.occupancy, v.contact
FROM (VALUES
  ('Marikina Sports Center',    14.634800, 121.096300, 1200, 740, '(02) 8646-1631'),
  ('Sta. Elena High School',    14.625900, 121.093000,  800, 800, '(02) 8997-1100'),
  ('Concepcion Covered Court',  14.654200, 121.105600,  600, 522, '161'),
  ('Cainta Municipal Hall',     14.576200, 121.122000,  450, 138, '(02) 8656-1500'),
  ('Riverbanks Evac Hub',       14.628500, 121.083900,  500, 300, '(02) 8632-1000')
) AS v(name, latitude, longitude, capacity, occupancy, contact)
WHERE NOT EXISTS (SELECT 1 FROM evacuation_center c WHERE c.name = v.name);

-- ============================================================================
-- 4. FLOOD_REPORT — sample active reports. Keyed by (name, district) for
--    idempotency. reporter_user_id is left NULL (system-seeded).
-- ============================================================================
INSERT INTO flood_report (name, district, latitude, longitude, severity, depth_cm, trend, road, reports_count, status)
SELECT v.name, v.district, v.latitude, v.longitude,
       v.severity::severity_t, v.depth_cm, v.trend::trend_t, v.road::road_t,
       v.reports_count, 'active'::report_status_t
FROM (VALUES
  ('San Mateo Bridge', 'San Mateo, Rizal', 14.696500, 121.121800, 'high',   118, 'rising',   'impassable', 18),
  ('Tumana',           'Marikina',         14.656300, 121.087500, 'high',   124, 'rising',   'impassable', 31),
  ('Concepcion Uno',   'Marikina',         14.654800, 121.097600, 'medium',  46, 'steady',   'risky',      12),
  ('Provident Village','Marikina',         14.640900, 121.103400, 'high',    96, 'rising',   'impassable', 41),
  ('Riverbanks Center','Marikina',         14.628500, 121.083900, 'medium',  52, 'steady',   'risky',       9),
  ('Rosario',          'Pasig',            14.585600, 121.090300, 'medium',  38, 'receding', 'risky',      14),
  ('Santolan',         'Pasig',            14.610200, 121.085400, 'low',     16, 'receding', 'passable',    6),
  ('Cainta Junction',  'Cainta, Rizal',    14.581900, 121.122400, 'low',     20, 'steady',   'passable',    7)
) AS v(name, district, latitude, longitude, severity, depth_cm, trend, road, reports_count)
WHERE NOT EXISTS (
  SELECT 1 FROM flood_report fr WHERE fr.name = v.name AND fr.district = v.district
);
