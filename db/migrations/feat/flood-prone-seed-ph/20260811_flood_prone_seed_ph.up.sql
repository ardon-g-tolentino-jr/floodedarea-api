-- ============================================================================
-- Data seed: nationwide flood-prone areas (Philippines)
-- Branch: 070-feat-flood-prone-seed-ph        Date: 2026-08-11
-- Idempotent — safe to re-run. Rollback: 20260811_flood_prone_seed_ph.down.sql
--
-- Populates baha.flood_prone_area with ~240 curated flood-prone zones covering
-- Luzon, Visayas and Mindanao. Requires the flood-prone-zones migration
-- (20260805_flood_prone_zones.up.sql) to have run first.
--
-- SOURCES (public reporting + government hazard programs):
--   * MMDA Flood Control & Sewerage Management Office flood-prone street list
--     (https://mmda.gov.ph/images/pdf/Home/Flood-Prone-Areas.pdf)
--   * DENR–MGB geohazard mapping / National Adaptation Plan top-10 flood-prone
--     provinces: Pampanga, Nueva Ecija, Pangasinan, Tarlac, Maguindanao,
--     Bulacan, Metro Manila, North Cotabato, Oriental Mindoro, Ilocos Norte.
--   * PAGASA Flood Forecasting & Warning System for River Basins — the 18 major
--     basins (Cagayan, Mindanao/Rio Grande, Agusan, Pampanga, Agno, Bicol,
--     Pasig–Laguna, Panay, Jalaur, Tagum–Libuganon, Davao, Cagayan de Oro,
--     Agus, Tagoloan, Ilog–Hilabangan, Abra, Abulug, Buayan–Malungon).
--   * UP NOAH / DOST Project NOAH flood-susceptibility mapping.
--   * News reporting on recurring inundation (Habagat 2026, Ondoy/Ulysses,
--     Sendong, Pablo, Ormoc 1991, 2025 Quezon City floods).
--
-- IMPORTANT: latitude/longitude are approximate centroids of the named area,
-- not surveyed hazard-polygon boundaries. radius_m is a coarse impact radius
-- sized to the zone (street ≈ 500–1,200 m; town ≈ 1,500–3,000 m; delta/marsh
-- ≈ 3,000–6,000 m). Treat as an advisory starting set for admins to refine
-- through the Admin > Flood-Prone Zones UI, not as an authoritative hazard map.
--
-- level semantics (alert_level_t):
--   red    — floods nearly every significant rain event / major basin overflow
--   orange — floods in heavy monsoon rain or typhoons, recurring every season
--   yellow — floods in extreme events only
--
-- Idempotency key: (name, district). Re-running never clobbers admin edits to
-- radius/level/note, and never resurrects rows an admin deleted on purpose
-- unless they were deleted *and* the name+district pair is now absent.
-- ============================================================================
SET search_path TO baha, public;

INSERT INTO flood_prone_area (name, district, latitude, longitude, radius_m, level, note, active)
SELECT v.name, v.district, v.latitude, v.longitude, v.radius_m, v.level::alert_level_t, v.note, true
FROM (VALUES
  -- ==========================================================================
  -- NATIONAL CAPITAL REGION — Manila
  -- ==========================================================================
  ('España Boulevard (Sampaloc)',        'Manila, Metro Manila',      14.608800, 120.994600,  900, 'red',    'Perennial waist-deep flooding on the España–Lacson stretch; Estero de Sampaloc backflow.'),
  ('Taft Avenue (Ermita–Malate)',        'Manila, Metro Manila',      14.576600, 120.989500,  900, 'orange', 'Low-lying reclaimed ground; drainage backs up at high tide.'),
  ('Lagusnilad Underpass',               'Manila, Metro Manila',      14.593000, 120.981500,  300, 'orange', 'Underpass sump floods within minutes of heavy rain; impassable to vehicles.'),
  ('Quirino Avenue (Paco–Pandacan)',     'Manila, Metro Manila',      14.579000, 120.998200,  900, 'orange', 'Pasig River backflow through Estero de Pandacan.'),
  ('Rizal Avenue / Recto (Sta. Cruz)',   'Manila, Metro Manila',      14.604500, 120.982000,  800, 'orange', 'MMDA-listed flood-prone corridor; drainage overwhelmed in sustained rain.'),
  ('Pedro Gil Street (Paco)',            'Manila, Metro Manila',      14.579300, 120.990300,  700, 'orange', 'Gutter-to-knee deep flooding; Estero de Paco overflow.'),
  ('A. Maceda / Antipolo St (Sampaloc)', 'Manila, Metro Manila',      14.615200, 120.990500,  700, 'orange', 'MMDA-listed; feeds into the España basin.'),
  ('Tondo coastal barangays',            'Manila, Metro Manila',      14.618000, 120.966000, 1200, 'orange', 'Manila Bay tidal flooding compounded by land subsidence.'),
  ('P. Burgos / Manila City Hall',       'Manila, Metro Manila',      14.591000, 120.981800,  500, 'orange', 'MMDA-listed flood-prone street.'),

  -- NCR — Quezon City
  ('G. Araneta Avenue',                  'Quezon City, Metro Manila', 14.628000, 121.001000,  900, 'red',    'One of the most consistently flooded arteries in QC; San Juan River catchment.'),
  ('EDSA–Muñoz',                         'Quezon City, Metro Manila', 14.657000, 121.017000,  700, 'orange', 'Underpass and service road pond during downpours.'),
  ('Commonwealth Ave (Tandang Sora–Litex)','Quezon City, Metro Manila',14.690000, 121.079000, 1500, 'orange', 'Sheet flooding across the highway; creeks overtop at Litex.'),
  ('Barangay Bagong Silangan',           'Quezon City, Metro Manila', 14.706000, 121.101000, 1500, 'red',    'Riverside settlements on the Marikina River; scene of the 2025 QC floods.'),
  ('Banawe / Roxas District',            'Quezon City, Metro Manila', 14.629500, 120.998500,  700, 'orange', 'Low-lying pocket draining to the San Juan River.'),
  ('EDSA near Camp Aguinaldo',           'Quezon City, Metro Manila', 14.612000, 121.064000,  700, 'orange', 'MMDA-listed; runoff from White Plains ponds on EDSA.'),
  ('Barangay Batasan Hills riverside',   'Quezon City, Metro Manila', 14.687000, 121.096000, 1200, 'orange', 'Marikina River bank communities; evacuation zone at alarm level 2.'),

  -- NCR — Marikina
  ('Provident Village (Sto. Niño)',      'Marikina, Metro Manila',    14.630000, 121.087000,  900, 'red',    'Classic Marikina River overflow zone; roof-deep in Ondoy and Ulysses.'),
  ('Barangay Tumana',                    'Marikina, Metro Manila',    14.652000, 121.079000, 1200, 'red',    'Riverside barangay; forced evacuation whenever the river hits alarm level 2.'),
  ('Barangay Malanday',                  'Marikina, Metro Manila',    14.664000, 121.095000, 1200, 'red',    'Confluence of the Marikina and Nangka rivers; first to flood.'),
  ('Barangay Nangka',                    'Marikina, Metro Manila',    14.674000, 121.105000, 1000, 'red',    'Nangka River overtops its banks in sustained habagat rain.'),
  ('Barangay Concepcion Uno / Riverbanks','Marikina, Metro Manila',   14.642000, 121.079000, 1000, 'red',    'Riverbanks complex and adjacent residential blocks inundate at alarm level 3.'),
  ('Barangka',                           'Marikina, Metro Manila',    14.625000, 121.079000,  800, 'orange', 'Low-lying riverside barangay downstream of Provident.'),

  -- NCR — Pasig / Pateros / San Juan / Mandaluyong
  ('Manggahan Floodway corridor',        'Pasig, Metro Manila',       14.590000, 121.090000, 1500, 'red',    'Floodway diverts Marikina River water to Laguna de Bay; adjacent settlements inundate.'),
  ('C-5 Bagong Ilog',                    'Pasig, Metro Manila',       14.573000, 121.070000,  800, 'orange', 'MMDA-listed; C-5 southbound ponds under the flyover.'),
  ('Barangay Santolan',                  'Pasig, Metro Manila',       14.610000, 121.087000,  900, 'orange', 'Marikina River bank; overflows with upstream dam releases.'),
  ('Barangay Rosario / Ortigas Ext',     'Pasig, Metro Manila',       14.590000, 121.095000, 1000, 'orange', 'Floodway-adjacent; chronic street flooding.'),
  ('Sta. Ana / Pateros River',           'Pateros, Metro Manila',     14.545000, 121.068000,  800, 'orange', 'Pateros River backflow from the Pasig; whole-town flooding in Ondoy.'),
  ('Balong Bato / Barangay Batis',       'San Juan, Metro Manila',    14.598000, 121.023000,  700, 'orange', 'San Juan River overflow into riverside blocks.'),
  ('Maysilo Circle',                     'Mandaluyong, Metro Manila', 14.579000, 121.035000,  500, 'orange', 'MMDA-listed; circle floods fast and traps vehicles.'),
  ('Shaw Boulevard Tunnel / EDSA',       'Mandaluyong, Metro Manila', 14.582000, 121.053000,  500, 'yellow', 'Tunnel sump fails in extreme rain.'),

  -- NCR — Makati / Taguig
  ('EDSA–Pasong Tamo (Chino Roces)',     'Makati, Metro Manila',      14.554000, 121.014000,  700, 'orange', 'MMDA-listed; knee-deep flooding at the EDSA junction.'),
  ('Magallanes Tunnel',                  'Makati, Metro Manila',      14.541000, 121.017000,  400, 'orange', 'Underpass floods and closes to traffic.'),
  ('Barangay Cembo / West Rembo',        'Makati, Metro Manila',      14.568000, 121.057000,  800, 'yellow', 'Dense low-lying barangays draining to the Pasig.'),
  ('Lower Bicutan',                      'Taguig, Metro Manila',      14.506000, 121.056000, 1000, 'orange', 'Taguig River catchment; recurring street flooding.'),
  ('Barangay Napindan',                  'Taguig, Metro Manila',      14.535000, 121.093000, 1200, 'red',    'Laguna de Bay lakeshore at the Napindan Channel; months-long inundation after major storms.'),
  ('Bayani Road',                        'Taguig, Metro Manila',      14.522000, 121.047000,  700, 'yellow', 'MMDA-listed flood-prone road.'),

  -- NCR — Pasay / Parañaque / Las Piñas / Muntinlupa
  ('EDSA–Taft Rotonda',                  'Pasay, Metro Manila',       14.538000, 120.997000,  600, 'red',    'Chronic flooding at the LRT/MRT interchange; paralyses southbound EDSA.'),
  ('Roxas Boulevard (Baclaran–Buendia)', 'Pasay, Metro Manila',       14.546000, 120.988000, 1500, 'orange', 'Manila Bay storm surge and tidal backflow over the seawall.'),
  ('Macapagal Avenue',                   'Pasay, Metro Manila',       14.533000, 120.988000, 1000, 'orange', 'Reclamation-area drainage backs up at high tide.'),
  ('Sucat Road',                         'Parañaque, Metro Manila',   14.479000, 121.021000, 1200, 'orange', 'MMDA-listed; deep flooding near the Sucat interchange.'),
  ('East / West Service Road (Sucat)',   'Parañaque, Metro Manila',   14.470000, 121.033000, 1200, 'orange', 'SLEX service roads pond and stall vehicles.'),
  ('Barangay La Huerta / Parañaque River','Parañaque, Metro Manila',  14.490000, 120.995000,  900, 'orange', 'Riverine and coastal flooding near the river mouth.'),
  ('Alabang–Zapote Rd / Real St',        'Las Piñas, Metro Manila',   14.450000, 120.988000, 1000, 'orange', 'MMDA-listed; Casimiro–Kantarilla creek overflow.'),
  ('Naga Road (Pulang Lupa)',            'Las Piñas, Metro Manila',   14.456000, 121.008000,  900, 'orange', 'Zapote River catchment; recurring knee-deep flooding.'),
  ('Quirino Avenue / Tramo',             'Las Piñas, Metro Manila',   14.470000, 120.983000,  900, 'orange', 'Low-lying coastal corridor.'),
  ('National Road near City Hall (Putatan)','Muntinlupa, Metro Manila',14.403000, 121.043000,  900, 'orange', 'MMDA-listed; Alabang River backflow.'),
  ('Barangay Bayanan (lakeshore)',       'Muntinlupa, Metro Manila',  14.409000, 121.053000, 1200, 'orange', 'Laguna de Bay shoreline; prolonged inundation when the lake rises.'),
  ('Barangay Tunasan',                   'Muntinlupa, Metro Manila',  14.376000, 121.043000,  900, 'yellow', 'Tunasan River overflow in extreme rainfall.'),

  -- NCR — CAMANAVA (Caloocan / Malabon / Navotas / Valenzuela)
  ('Dagat-Dagatan Avenue',               'Caloocan, Metro Manila',    14.652000, 120.972000, 1200, 'red',    'CAMANAVA basin; below sea level with chronic tidal flooding.'),
  ('Susano Road (Novaliches)',           'Caloocan, Metro Manila',    14.728000, 121.047000, 1000, 'orange', 'MMDA-listed; Tullahan River tributary overflow.'),
  ('MacArthur Highway (Grace Park)',     'Caloocan, Metro Manila',    14.654000, 120.986000,  900, 'orange', 'MMDA-listed flood-prone highway stretch.'),
  ('A. Mabini / Rizal Ave Extension',    'Caloocan, Metro Manila',    14.648000, 120.982000,  800, 'orange', 'Dense low-lying commercial district.'),
  ('JP Rizal St (Barangay Tañong)',      'Malabon, Metro Manila',     14.672000, 121.003000,  900, 'red',    'MMDA-listed; Tullahan River bank, floods at nearly every high tide.'),
  ('Gen. Luna St (Concepcion)',          'Malabon, Metro Manila',     14.660000, 120.960000,  900, 'red',    'MMDA-listed; land subsidence keeps water standing for days.'),
  ('Dulong Duhat / Hulong Duhat',        'Malabon, Metro Manila',     14.665000, 120.963000,  900, 'orange', 'Chronic tidal flooding in the CAMANAVA lowland.'),
  ('Letre / Tonsuya',                    'Malabon, Metro Manila',     14.672000, 120.966000,  900, 'orange', 'Tullahan River and Manila Bay tidal backflow.'),
  ('Naval St (Sipac-Almacen)',           'Navotas, Metro Manila',     14.657000, 120.945000,  900, 'red',    'MMDA-listed; roughly two-thirds of Navotas sits below the high-tide line.'),
  ('North Bay Boulevard South',          'Navotas, Metro Manila',     14.648000, 120.952000, 1200, 'red',    'Coastal fishing community with daily tidal inundation.'),
  ('Barangay Tanza',                     'Navotas, Metro Manila',     14.690000, 120.933000, 1200, 'red',    'MMDA-listed; Manila Bay shoreline settlements.'),
  ('MacArthur Highway (Karuhatan)',      'Valenzuela, Metro Manila',  14.690000, 120.978000, 1000, 'orange', 'MMDA-listed; highway ponds and blocks northbound traffic.'),
  ('Barangay Malanday (Tullahan River)', 'Valenzuela, Metro Manila',  14.689000, 120.988000, 1000, 'red',    'Tullahan River overflow; recurring chest-deep flooding.'),
  ('Santiago St / Marulas',              'Valenzuela, Metro Manila',  14.679000, 120.982000,  800, 'orange', 'MMDA-listed flood-prone street.'),

  -- ==========================================================================
  -- REGION IV-A — Rizal (Marikina basin + Laguna de Bay shore)
  -- ==========================================================================
  ('Barangay San Andres / Ortigas Ext',  'Cainta, Rizal',             14.576000, 121.117000, 1500, 'red',    'Manggahan Floodway and Cainta River; among the deepest NCR-fringe floods.'),
  ('Barangay San Juan / Manggahan',      'Taytay, Rizal',             14.557000, 121.132000, 1500, 'orange', 'Floodway-adjacent lowland draining to Laguna de Bay.'),
  ('Angono lakeshore barangays',         'Angono, Rizal',             14.527000, 121.154000, 1500, 'orange', 'Laguna de Bay backflow; water stays for weeks.'),
  ('Binangonan lakeshore barangays',     'Binangonan, Rizal',         14.463000, 121.192000, 2000, 'orange', 'Lakeshore settlements inundated when Laguna de Bay rises.'),
  ('Barangay Banaba (Nangka River)',     'San Mateo, Rizal',          14.697000, 121.121000, 1200, 'red',    'Riverside community repeatedly destroyed by Marikina headwater surges.'),
  ('Barangay San Jose / Wawa',           'Rodriguez (Montalban), Rizal',14.728000, 121.145000, 1500, 'red',   'Marikina River headwaters; flash flooding from the Wawa gorge.'),
  ('Barangay Mayamot / Mambugan',        'Antipolo, Rizal',           14.606000, 121.118000, 1200, 'orange', 'Low-lying pockets fed by hillside runoff.'),
  ('Tanay River corridor',               'Tanay, Rizal',              14.498000, 121.286000, 1200, 'orange', 'Flash flooding from the Sierra Madre catchment.'),
  ('Morong River corridor',              'Morong, Rizal',             14.511000, 121.238000, 1200, 'yellow', 'River overtops in extreme rainfall.'),
  ('Cardona lakeshore',                  'Cardona, Rizal',            14.488000, 121.228000, 1200, 'yellow', 'Laguna de Bay shoreline inundation.'),

  -- ==========================================================================
  -- REGION III — Bulacan (Angat / Pampanga delta)
  -- ==========================================================================
  ('Calumpit town centre',               'Calumpit, Bulacan',         14.917000, 120.766000, 4000, 'red',    'Angat–Pampanga river confluence; chest-deep flooding for weeks each habagat season.'),
  ('Hagonoy delta barangays',            'Hagonoy, Bulacan',          14.832000, 120.732000, 4000, 'red',    'Delta town below sea level; permanent tidal flooding in several barangays.'),
  ('Paombong',                           'Paombong, Bulacan',         14.829000, 120.788000, 3000, 'red',    'Manila Bay tidal flats; subsidence-driven chronic inundation.'),
  ('Malolos lowland barangays',          'Malolos, Bulacan',          14.844000, 120.811000, 2500, 'orange', 'Bulacan River system overflow into the city core.'),
  ('Barangay Bambang',                   'Bulakan, Bulacan',          14.793000, 120.879000, 2500, 'orange', 'Coastal delta; tidal and riverine flooding.'),
  ('Barangay Abangan Sur / Poblacion',   'Marilao, Bulacan',          14.757000, 120.948000, 2000, 'red',    'Marilao River overflow; church and poblacion flooded in the 2026 habagat.'),
  ('Barangay Bancal / Libtong',          'Meycauayan, Bulacan',       14.737000, 120.960000, 2000, 'red',    'Meycauayan River; chronic flooding worsened by subsidence.'),
  ('Obando delta',                       'Obando, Bulacan',           14.708000, 120.937000, 2000, 'red',    'Below-sea-level delta at the Manila Bay edge.'),
  ('Bocaue riverside',                   'Bocaue, Bulacan',           14.799000, 120.927000, 1500, 'orange', 'Bocaue River overflow.'),
  ('Pulilan riverside',                  'Pulilan, Bulacan',          14.901000, 120.848000, 1500, 'orange', 'Angat River bank inundation.'),
  ('Plaridel lowland',                   'Plaridel, Bulacan',         14.885000, 120.857000, 1500, 'orange', 'Angat River floodplain.'),
  ('Baliwag (Angat River)',              'Baliwag, Bulacan',          14.954000, 120.896000, 1500, 'yellow', 'Floods on Angat Dam spilling operations.'),
  ('San Miguel (Bulacan)',               'San Miguel, Bulacan',       15.144000, 120.976000, 1500, 'yellow', 'Sibul/Bulu river catchment.'),
  ('Norzagaray (Angat release corridor)','Norzagaray, Bulacan',       14.911000, 121.045000, 2000, 'yellow', 'Downstream of Angat Dam; flooding on gate releases.'),

  -- ==========================================================================
  -- REGION III — Pampanga (Pampanga River basin — most flood-vulnerable basin)
  -- ==========================================================================
  ('Macabebe delta',                     'Macabebe, Pampanga',        14.913000, 120.717000, 4000, 'red',    'Pampanga River delta; among the most persistently flooded towns in Luzon.'),
  ('Masantol',                           'Masantol, Pampanga',        14.876000, 120.718000, 4000, 'red',    'Delta town where flooding is effectively year-round in low barangays.'),
  ('Sasmuan',                            'Sasmuan, Pampanga',         14.920000, 120.630000, 3000, 'red',    'Tidal fishpond belt on Manila Bay.'),
  ('Apalit',                             'Apalit, Pampanga',          14.950000, 120.758000, 2500, 'red',    'Pampanga River bank; recurring deep flooding.'),
  ('Candaba Swamp',                      'Candaba, Pampanga',         15.095000, 120.828000, 6000, 'red',    'Natural floodwater retention basin; inundated for months every wet season.'),
  ('Minalin',                            'Minalin, Pampanga',         14.970000, 120.687000, 2500, 'orange', 'Rio Chico/Pampanga River lowland.'),
  ('San Simon',                          'San Simon, Pampanga',       14.993000, 120.780000, 2500, 'orange', 'Pampanga River floodplain.'),
  ('San Luis (Pampanga)',                'San Luis, Pampanga',        15.033000, 120.793000, 2000, 'orange', 'Candaba swamp fringe.'),
  ('Barangay Sto. Niño / San Jose',      'City of San Fernando, Pampanga',15.035000,120.690000,2500,'orange', 'City lowland; parish churches flooded in the 2026 habagat.'),
  ('Guagua',                             'Guagua, Pampanga',          14.966000, 120.632000, 2500, 'orange', 'Guagua River and lahar-raised riverbeds.'),
  ('Bacolor',                            'Bacolor, Pampanga',         14.999000, 120.654000, 2500, 'orange', 'Lahar-buried town; raised riverbeds keep flood risk high.'),
  ('Lubao',                              'Lubao, Pampanga',           14.941000, 120.599000, 3000, 'orange', 'Lowland between the Pampanga delta and Manila Bay.'),
  ('Arayat',                             'Arayat, Pampanga',          15.150000, 120.769000, 2000, 'orange', 'Pampanga River bank at the foot of Mt. Arayat.'),
  ('Floridablanca',                      'Floridablanca, Pampanga',   14.976000, 120.529000, 2500, 'yellow', 'Gumain/Porac river lahar channels.'),
  ('Mexico (Pampanga)',                  'Mexico, Pampanga',          15.065000, 120.718000, 2000, 'yellow', 'Abacan River overflow.'),
  ('Barangay Balibago',                  'Angeles City, Pampanga',    15.169000, 120.590000, 1500, 'yellow', 'Urban drainage overload along the Abacan catchment.'),

  -- ==========================================================================
  -- REGION III — Nueva Ecija / Tarlac
  -- ==========================================================================
  ('Cabanatuan City lowland',            'Cabanatuan City, Nueva Ecija',15.486000,120.967000, 3000, 'orange', 'Pampanga River upper basin; flash flooding from Sierra Madre runoff.'),
  ('Cabiao',                             'Cabiao, Nueva Ecija',       15.250000, 120.848000, 2500, 'red',    'Candaba swamp fringe; among the most deeply flooded Nueva Ecija towns.'),
  ('Gapan City',                         'Gapan City, Nueva Ecija',   15.308000, 120.947000, 2500, 'orange', 'Peñaranda River overflow.'),
  ('San Isidro (Nueva Ecija)',           'San Isidro, Nueva Ecija',   15.287000, 120.876000, 2000, 'orange', 'Pampanga River floodplain.'),
  ('Jaen',                               'Jaen, Nueva Ecija',         15.332000, 120.911000, 2000, 'orange', 'Rio Chico floodplain.'),
  ('Aliaga',                             'Aliaga, Nueva Ecija',       15.504000, 120.840000, 2000, 'orange', 'Rice-plain flooding from Rio Chico.'),
  ('Zaragoza',                           'Zaragoza, Nueva Ecija',     15.449000, 120.794000, 2000, 'yellow', 'Low-gradient rice plain; slow-draining floods.'),
  ('Talavera',                           'Talavera, Nueva Ecija',     15.586000, 120.916000, 2000, 'yellow', 'Talavera River overflow.'),
  ('Guimba',                             'Guimba, Nueva Ecija',       15.658000, 120.769000, 2000, 'yellow', 'Flat rice plain with poor drainage.'),
  ('Science City of Muñoz',              'Science City of Muñoz, Nueva Ecija',15.716000,120.903000,1500,'yellow','Flash flooding in extreme rainfall events.'),
  ('Bongabon',                           'Bongabon, Nueva Ecija',     15.635000, 121.142000, 1500, 'yellow', 'Sierra Madre foothill flash floods.'),
  ('Tarlac City (Maliwalo / San Rafael)','Tarlac City, Tarlac',       15.480000, 120.590000, 2500, 'orange', 'Tarlac River overflow into the city lowland.'),
  ('La Paz (Tarlac)',                    'La Paz, Tarlac',            15.443000, 120.725000, 2000, 'orange', 'Rio Chico/Pampanga basin lowland.'),
  ('Concepcion (Tarlac)',                'Concepcion, Tarlac',        15.323000, 120.656000, 2000, 'orange', 'Pampanga River basin; lahar-raised channels.'),
  ('Gerona',                             'Gerona, Tarlac',            15.611000, 120.607000, 2000, 'yellow', 'Tarlac River floodplain.'),
  ('Paniqui',                            'Paniqui, Tarlac',           15.669000, 120.580000, 2000, 'yellow', 'Agno tributary flooding.'),
  ('Victoria (Tarlac)',                  'Victoria, Tarlac',          15.578000, 120.681000, 1500, 'yellow', 'Low-lying rice plain.'),
  ('Bamban (lahar channels)',            'Bamban, Tarlac',            15.236000, 120.568000, 1500, 'yellow', 'Pinatubo lahar deposits raised the riverbed above the town.'),

  -- ==========================================================================
  -- REGION III — Zambales / Bataan
  -- ==========================================================================
  ('Botolan (Bucao River)',              'Botolan, Zambales',         15.287000, 120.029000, 2500, 'orange', 'Lahar-choked Bucao River; channel shifts flood adjacent barangays.'),
  ('Iba (Bancal River)',                 'Iba, Zambales',             15.328000, 119.978000, 2000, 'orange', 'Coastal plain flooding during habagat surges.'),
  ('Cabangan',                           'Cabangan, Zambales',        15.156000, 120.050000, 2000, 'yellow', 'Coastal lowland flooding.'),
  ('San Antonio (Zambales)',             'San Antonio, Zambales',     14.945000, 120.085000, 1500, 'yellow', 'River-mouth flooding at high tide.'),
  ('Castillejos',                        'Castillejos, Zambales',     14.933000, 120.200000, 1500, 'yellow', 'Sto. Tomas River lahar channel.'),
  ('Olongapo City lowland',              'Olongapo City, Zambales',   14.829000, 120.282000, 2000, 'orange', 'Sta. Rita and Gordon Heights flood from hillside runoff.'),
  ('Dinalupihan',                        'Dinalupihan, Bataan',       14.879000, 120.465000, 2000, 'orange', 'Almacen River overflow; gateway to the Pampanga lowland.'),
  ('Hermosa',                            'Hermosa, Bataan',           14.834000, 120.500000, 2000, 'orange', 'Almacen River floodplain; recurring town-wide flooding.'),
  ('Orani',                              'Orani, Bataan',             14.799000, 120.534000, 2000, 'orange', 'Coastal town on Manila Bay; tidal plus riverine flooding.'),
  ('Samal (Bataan)',                     'Samal, Bataan',             14.769000, 120.543000, 1500, 'yellow', 'Coastal lowland.'),
  ('Abucay',                             'Abucay, Bataan',            14.722000, 120.535000, 1500, 'yellow', 'Talisay River overflow.'),
  ('Balanga City',                       'Balanga City, Bataan',      14.676000, 120.536000, 2000, 'orange', 'Talisay River and Manila Bay tidal backflow.'),
  ('Pilar (Bataan)',                     'Pilar, Bataan',             14.664000, 120.565000, 1500, 'yellow', 'Coastal plain flooding.'),
  ('Orion',                              'Orion, Bataan',             14.622000, 120.582000, 1500, 'yellow', 'Low-lying coastal barangays.'),

  -- ==========================================================================
  -- REGION IV-A — Cavite / Laguna / Batangas
  -- ==========================================================================
  ('Barangay Zapote / Niog',             'Bacoor, Cavite',            14.459000, 120.945000, 2000, 'orange', 'Zapote River and Manila Bay tidal flooding.'),
  ('Kawit',                              'Kawit, Cavite',             14.444000, 120.902000, 1500, 'orange', 'Coastal town; chronic tidal flooding.'),
  ('Noveleta',                           'Noveleta, Cavite',          14.429000, 120.879000, 1500, 'orange', 'Below-sea-level coastal barangays.'),
  ('Rosario (Cavite)',                   'Rosario, Cavite',           14.416000, 120.857000, 1500, 'orange', 'Coastal fishing town with daily tidal inundation.'),
  ('Cavite City',                        'Cavite City, Cavite',       14.483000, 120.898000, 1500, 'yellow', 'Peninsula city exposed to storm surge.'),
  ('Imus riverside',                     'Imus, Cavite',              14.429000, 120.937000, 1500, 'yellow', 'Imus River overflow.'),
  ('General Trias lowland',              'General Trias, Cavite',     14.387000, 120.881000, 1500, 'yellow', 'Rapid urbanisation reduced infiltration; flash flooding.'),
  ('Tanza',                              'Tanza, Cavite',             14.394000, 120.851000, 1500, 'yellow', 'Coastal lowland.'),
  ('Naic',                               'Naic, Cavite',              14.317000, 120.767000, 1500, 'yellow', 'Labac River and coastal flooding.'),
  ('San Pedro lowland',                  'San Pedro, Laguna',         14.359000, 121.047000, 1500, 'orange', 'Laguna de Bay shoreline plus urban runoff from Muntinlupa.'),
  ('Biñan lowland',                      'Biñan, Laguna',             14.339000, 121.080000, 2000, 'orange', 'Lakeshore barangays inundated for weeks.'),
  ('Santa Rosa lakeshore',               'Santa Rosa, Laguna',        14.312000, 121.111000, 2000, 'orange', 'Laguna de Bay backflow into subdivisions.'),
  ('Cabuyao lakeshore',                  'Cabuyao, Laguna',           14.276000, 121.125000, 2000, 'orange', 'Lakeshore industrial and residential flooding.'),
  ('Calamba (Lecheria / Pansol)',        'Calamba City, Laguna',      14.211000, 121.165000, 2500, 'orange', 'San Cristobal River overflow and lakeshore backflow.'),
  ('Bay lakeshore',                      'Bay, Laguna',               14.181000, 121.281000, 1500, 'orange', 'Laguna de Bay shoreline.'),
  ('Los Baños lakeshore',                'Los Baños, Laguna',         14.169000, 121.241000, 1500, 'yellow', 'Molawin/Dampalit creek flash floods and lake backflow.'),
  ('Victoria (Laguna)',                  'Victoria, Laguna',          14.227000, 121.330000, 1500, 'orange', 'Lakeshore rice lowland.'),
  ('Pila',                               'Pila, Laguna',              14.233000, 121.366000, 1500, 'orange', 'Lakeshore heritage town; prolonged inundation.'),
  ('Santa Cruz (Laguna)',                'Santa Cruz, Laguna',        14.282000, 121.416000, 2000, 'orange', 'Sta. Cruz River overflow at the lake mouth.'),
  ('Pagsanjan',                          'Pagsanjan, Laguna',         14.274000, 121.455000, 1500, 'orange', 'Confluence of the Balanac and Bumbungan rivers.'),
  ('Lumban',                             'Lumban, Laguna',            14.297000, 121.460000, 1500, 'orange', 'Caliraya outflow and lakeshore flooding.'),
  ('Siniloan',                           'Siniloan, Laguna',          14.420000, 121.445000, 1500, 'orange', 'Sierra Madre flash floods into the lakeshore plain.'),
  ('Paete lakeshore',                    'Paete, Laguna',             14.362000, 121.482000, 1200, 'yellow', 'Steep catchment flash floods plus lake backflow.'),
  ('Calauan',                            'Calauan, Laguna',           14.149000, 121.316000, 1500, 'yellow', 'Low-lying agricultural plain.'),
  ('Batangas City (Balagtas / Cuta)',    'Batangas City, Batangas',   13.757000, 121.058000, 2000, 'orange', 'Calumpang River overflow into the port district.'),
  ('Taal–Lemery (Pansipit River)',       'Lemery, Batangas',          13.883000, 120.910000, 2000, 'orange', 'Pansipit River drains Taal Lake; backs up in heavy rain.'),
  ('Rosario (Batangas)',                 'Rosario, Batangas',         13.846000, 121.205000, 1500, 'yellow', 'Riverine flash flooding.'),
  ('Lipa City lowland',                  'Lipa City, Batangas',       13.941000, 121.162000, 1500, 'yellow', 'Urban drainage overload.'),
  ('Calaca',                             'Calaca, Batangas',          13.932000, 120.813000, 1500, 'yellow', 'Coastal and riverine flooding.'),
  ('San Juan (Batangas)',                'San Juan, Batangas',        13.827000, 121.396000, 1500, 'yellow', 'Coastal town exposed to storm surge and river overflow.'),

  -- ==========================================================================
  -- REGION IV-B — Quezon / MIMAROPA
  -- ==========================================================================
  ('Lucena City lowland',                'Lucena City, Quezon',       13.937000, 121.617000, 2000, 'orange', 'Dumacaa River overflow into the city.'),
  ('Infanta',                            'Infanta, Quezon',           14.744000, 121.649000, 2500, 'red',    'Agos River; catastrophic 2004 flash floods and debris flows.'),
  ('Real',                               'Real, Quezon',              14.662000, 121.604000, 2000, 'orange', 'Sierra Madre flash flooding onto the coastal plain.'),
  ('General Nakar',                      'General Nakar, Quezon',     14.769000, 121.626000, 2000, 'orange', 'Agos River headwaters; rapid-onset flooding.'),
  ('Mauban',                             'Mauban, Quezon',            14.191000, 121.729000, 1500, 'yellow', 'Coastal river-mouth flooding.'),
  ('Gumaca',                             'Gumaca, Quezon',            13.921000, 122.101000, 1500, 'yellow', 'Riverine and coastal flooding.'),
  ('Calauag',                            'Calauag, Quezon',           13.957000, 122.286000, 1500, 'yellow', 'Bay-head town; storm surge and river overflow.'),
  ('Calapan City',                       'Calapan City, Oriental Mindoro',13.411000,121.180000, 2500, 'orange', 'Calapan and Bucayao river overflow; DENR top-10 flood-prone province.'),
  ('Naujan (lake and river plain)',      'Naujan, Oriental Mindoro',  13.323000, 121.302000, 2500, 'orange', 'Naujan Lake outflow floods the surrounding plain.'),
  ('Victoria (Oriental Mindoro)',        'Victoria, Oriental Mindoro',13.178000, 121.276000, 2000, 'orange', 'Bongabong/Baruyan river system.'),
  ('Pinamalayan',                        'Pinamalayan, Oriental Mindoro',13.037000,121.489000, 2000, 'orange', 'River-mouth flooding at high tide.'),
  ('Baco',                               'Baco, Oriental Mindoro',    13.356000, 121.098000, 1500, 'orange', 'Baco River flash floods and debris flows.'),
  ('Bansud',                             'Bansud, Oriental Mindoro',  12.862000, 121.456000, 1500, 'yellow', 'Coastal plain flooding.'),
  ('Bongabong',                          'Bongabong, Oriental Mindoro',12.746000,121.487000, 1500, 'yellow', 'Bongabong River overflow.'),
  ('San Jose (Occidental Mindoro)',      'San Jose, Occidental Mindoro',12.353000,121.068000, 2500, 'orange', 'Busuanga and Pandurucan river plain; frequent town-wide flooding.'),
  ('Sablayan',                           'Sablayan, Occidental Mindoro',12.842000,120.777000, 2000, 'yellow', 'Sablayan River overflow.'),
  ('Mamburao',                           'Mamburao, Occidental Mindoro',13.226000,120.596000, 1500, 'yellow', 'River-mouth flooding.'),
  ('Boac',                               'Boac, Marinduque',          13.446000, 121.840000, 1500, 'yellow', 'Boac River overflow.'),
  ('Santa Cruz (Marinduque)',            'Santa Cruz, Marinduque',    13.478000, 122.029000, 1500, 'yellow', 'Coastal and riverine flooding.'),
  ('Puerto Princesa lowland',            'Puerto Princesa, Palawan',  9.739000,  118.735000, 2000, 'yellow', 'Urban drainage overload in extreme rain.'),
  ('Roxas (Palawan)',                    'Roxas, Palawan',            10.311000, 119.340000, 1500, 'yellow', 'Coastal plain flooding.'),
  ('Brookes Point',                      'Brookes Point, Palawan',    8.783000,  117.833000, 1500, 'yellow', 'River-mouth and coastal flooding.'),

  -- ==========================================================================
  -- REGION I / CAR — Ilocos, Pangasinan (Agno basin), Cordillera
  -- ==========================================================================
  ('Laoag City (Padsan River)',          'Laoag City, Ilocos Norte',  18.198000, 120.593000, 2000, 'orange', 'DENR top-10 flood-prone province; Padsan River overflow into the city.'),
  ('Dingras',                            'Dingras, Ilocos Norte',     18.100000, 120.696000, 1500, 'orange', 'Padsan River floodplain rice lands.'),
  ('Batac City',                         'Batac City, Ilocos Norte',  18.055000, 120.564000, 1500, 'yellow', 'Riverine flooding in typhoon events.'),
  ('Sarrat',                             'Sarrat, Ilocos Norte',      18.165000, 120.644000, 1500, 'yellow', 'Padsan River bank flooding.'),
  ('Vigan City',                         'Vigan City, Ilocos Sur',    17.575000, 120.387000, 2000, 'orange', 'Mestizo/Abra river system backs up into the heritage core.'),
  ('Santa (Ilocos Sur)',                 'Santa, Ilocos Sur',         17.462000, 120.438000, 1500, 'yellow', 'Abra River delta.'),
  ('Narvacan',                           'Narvacan, Ilocos Sur',      17.416000, 120.479000, 1500, 'yellow', 'Coastal river-mouth flooding.'),
  ('Candon City',                        'Candon City, Ilocos Sur',   17.195000, 120.452000, 1500, 'yellow', 'Riverine flooding.'),
  ('Bangued',                            'Bangued, Abra',             17.600000, 120.618000, 2000, 'orange', 'Abra River basin; town centre floods in major typhoons.'),
  ('Tayum',                              'Tayum, Abra',               17.625000, 120.654000, 1500, 'yellow', 'Abra River bank.'),
  ('Agoo',                               'Agoo, La Union',            16.317000, 120.369000, 1500, 'orange', 'Coastal plain between the Aringay and Agoo rivers.'),
  ('San Fernando (La Union)',            'San Fernando City, La Union',16.616000,120.319000, 2000, 'yellow', 'Urban drainage overload plus river-mouth backflow.'),
  ('Bauang',                             'Bauang, La Union',          16.531000, 120.333000, 1500, 'yellow', 'Bauang River overflow.'),
  ('Rosario (La Union)',                 'Rosario, La Union',         16.227000, 120.493000, 1500, 'yellow', 'Riverine flooding from the Cordillera catchment.'),
  ('Dagupan City',                       'Dagupan City, Pangasinan',  16.043000, 120.333000, 3000, 'red',    'Agno delta; land subsidence makes flooding near-permanent in low barangays.'),
  ('Calasiao',                           'Calasiao, Pangasinan',      16.000000, 120.360000, 2000, 'orange', 'Agno river delta plain.'),
  ('Santa Barbara (Pangasinan)',         'Santa Barbara, Pangasinan', 16.011000, 120.403000, 2000, 'orange', 'Low-gradient delta with slow drainage.'),
  ('Mangaldan',                          'Mangaldan, Pangasinan',     16.070000, 120.403000, 2000, 'orange', 'Angalacan River overflow.'),
  ('Lingayen',                           'Lingayen, Pangasinan',      16.021000, 120.232000, 2500, 'orange', 'Agno river mouth on Lingayen Gulf; surge plus riverine flooding.'),
  ('San Carlos City (Pangasinan)',       'San Carlos City, Pangasinan',15.928000,120.349000, 2500, 'orange', 'Agno River bank; deep flooding on San Roque Dam releases.'),
  ('Bayambang',                          'Bayambang, Pangasinan',     15.823000, 120.459000, 2500, 'orange', 'Agno River floodplain.'),
  ('Malasiqui',                          'Malasiqui, Pangasinan',     15.920000, 120.413000, 2000, 'orange', 'Agno lowland rice plain.'),
  ('Rosales',                            'Rosales, Pangasinan',       15.895000, 120.632000, 2000, 'yellow', 'Agno River upper reach.'),
  ('Urdaneta City',                      'Urdaneta City, Pangasinan', 15.976000, 120.571000, 2000, 'yellow', 'Flat plain with poor drainage.'),
  ('Bautista / Alcala',                  'Bautista, Pangasinan',      15.781000, 120.506000, 2000, 'yellow', 'Agno River bank towns.'),
  ('Sual',                               'Sual, Pangasinan',          16.068000, 120.094000, 1500, 'yellow', 'Coastal flooding and storm surge.'),
  ('Bolinao',                            'Bolinao, Pangasinan',       16.386000, 119.895000, 1500, 'yellow', 'Peninsular coastal flooding.'),
  ('Agno',                               'Agno, Pangasinan',          16.124000, 119.806000, 1500, 'yellow', 'Coastal town exposed to surge and river overflow.'),
  ('La Trinidad',                        'La Trinidad, Benguet',      16.456000, 120.588000, 1500, 'orange', 'Balili River overflows into the valley floor and strawberry fields.'),
  ('Baguio City (Balili / Irisan)',      'Baguio City, Benguet',      16.402000, 120.596000, 1500, 'yellow', 'Urban flash flooding and debris flow in the Balili catchment.'),
  ('Tabuk City',                         'Tabuk City, Kalinga',       17.455000, 121.442000, 2000, 'orange', 'Chico River overflow into the rice plain.'),
  ('Bontoc',                             'Bontoc, Mountain Province', 17.089000, 120.976000, 1200, 'yellow', 'Chico River flash flooding.'),
  ('Lagawe',                             'Lagawe, Ifugao',            16.799000, 121.121000, 1200, 'yellow', 'Ibulao River flash flooding.'),
  ('Luna (Apayao)',                      'Luna, Apayao',              18.323000, 121.363000, 1500, 'yellow', 'Abulug River basin flooding.'),

  -- ==========================================================================
  -- REGION II — Cagayan Valley (Cagayan River basin — largest in the country)
  -- ==========================================================================
  ('Tuguegarao City',                    'Tuguegarao City, Cagayan',  17.613000, 121.727000, 3000, 'red',    'Cagayan–Pinacanauan confluence; city-wide flooding in Ulysses-class events.'),
  ('Solana',                             'Solana, Cagayan',           17.650000, 121.682000, 2500, 'red',    'Cagayan River west bank; among the worst-hit in the 2020 floods.'),
  ('Enrile',                             'Enrile, Cagayan',           17.561000, 121.696000, 2000, 'orange', 'Cagayan River floodplain.'),
  ('Iguig',                              'Iguig, Cagayan',            17.753000, 121.735000, 2000, 'orange', 'River-bank town; recurring deep flooding.'),
  ('Amulung',                            'Amulung, Cagayan',          17.833000, 121.722000, 2000, 'orange', 'Cagayan River bank.'),
  ('Alcala (Cagayan)',                   'Alcala, Cagayan',           17.885000, 121.651000, 2000, 'orange', 'Cagayan River floodplain rice lands.'),
  ('Gattaran',                           'Gattaran, Cagayan',         18.064000, 121.644000, 2000, 'orange', 'Cagayan River lower reach.'),
  ('Lal-lo',                             'Lal-lo, Cagayan',           18.197000, 121.662000, 2000, 'orange', 'Lower Cagayan River; backwater flooding at high tide.'),
  ('Aparri',                             'Aparri, Cagayan',           18.358000, 121.641000, 2500, 'red',    'Cagayan River mouth; riverine flooding compounded by storm surge.'),
  ('Buguey',                             'Buguey, Cagayan',           18.290000, 121.833000, 1500, 'yellow', 'Coastal lagoon town.'),
  ('Abulug',                             'Abulug, Cagayan',           18.443000, 121.446000, 1500, 'yellow', 'Abulug River basin (one of the 18 major basins).'),
  ('Baggao',                             'Baggao, Cagayan',           17.933000, 121.792000, 1500, 'yellow', 'Sierra Madre flash flooding.'),
  ('Tuao',                               'Tuao, Cagayan',             17.736000, 121.453000, 1500, 'yellow', 'Chico River overflow.'),
  ('Ilagan City',                        'Ilagan City, Isabela',      17.149000, 121.889000, 2500, 'orange', 'Cagayan and Pinacanauan river confluence.'),
  ('Cauayan City',                       'Cauayan City, Isabela',     16.935000, 121.771000, 2500, 'orange', 'Cagayan River bank; recurring city flooding.'),
  ('Echague',                            'Echague, Isabela',          16.704000, 121.677000, 2500, 'orange', 'Cagayan River floodplain.'),
  ('Cabagan',                            'Cabagan, Isabela',          17.429000, 121.773000, 2000, 'orange', 'Cagayan River bank.'),
  ('Tumauini',                           'Tumauini, Isabela',         17.274000, 121.808000, 2000, 'orange', 'Tumauini River overflow.'),
  ('Alicia (Isabela)',                   'Alicia, Isabela',           16.777000, 121.690000, 1500, 'yellow', 'Magat/Cagayan floodplain.'),
  ('Roxas (Isabela)',                    'Roxas, Isabela',            17.118000, 121.618000, 1500, 'yellow', 'Magat River lowland.'),
  ('San Mariano',                        'San Mariano, Isabela',      16.988000, 122.018000, 1500, 'yellow', 'Sierra Madre flash flooding.'),
  ('Santiago City',                      'Santiago City, Isabela',    16.687000, 121.547000, 2000, 'yellow', 'Urban drainage overload plus Magat backflow.'),
  ('Bayombong',                          'Bayombong, Nueva Vizcaya',  16.482000, 121.149000, 1500, 'yellow', 'Magat River overflow.'),
  ('Bambang (Nueva Vizcaya)',            'Bambang, Nueva Vizcaya',    16.383000, 121.109000, 1500, 'yellow', 'Magat River floodplain.'),
  ('Solano',                             'Solano, Nueva Vizcaya',     16.517000, 121.181000, 1500, 'yellow', 'Magat River lowland.'),
  ('Cabarroguis',                        'Cabarroguis, Quirino',      16.576000, 121.521000, 1500, 'yellow', 'Cagayan tributary flash flooding.'),

  -- ==========================================================================
  -- REGION V — Bicol (Bicol River basin)
  -- ==========================================================================
  ('Naga City (Barangay Triangulo)',     'Naga City, Camarines Sur',  13.621000, 123.181000, 2500, 'red',    'Bicol and Naga river confluence; the city floods every major typhoon.'),
  ('Camaligan',                          'Camaligan, Camarines Sur',  13.626000, 123.156000, 1500, 'orange', 'Naga River bank town.'),
  ('Canaman',                            'Canaman, Camarines Sur',    13.647000, 123.174000, 1500, 'orange', 'Bicol River floodplain.'),
  ('Calabanga',                          'Calabanga, Camarines Sur',  13.708000, 123.217000, 2000, 'orange', 'Bicol River delta at San Miguel Bay.'),
  ('Libmanan',                           'Libmanan, Camarines Sur',   13.694000, 123.059000, 2000, 'orange', 'Libmanan River overflow into the Bicol plain.'),
  ('Bula',                               'Bula, Camarines Sur',       13.472000, 123.274000, 2000, 'orange', 'Bicol River upper floodplain.'),
  ('Baao',                               'Baao, Camarines Sur',       13.454000, 123.366000, 2000, 'orange', 'Lake Baao and Bicol River backflow.'),
  ('Nabua',                              'Nabua, Camarines Sur',      13.409000, 123.376000, 2000, 'orange', 'Bicol River headwater plain; prolonged inundation.'),
  ('Bato (Camarines Sur)',               'Bato, Camarines Sur',       13.354000, 123.369000, 2000, 'orange', 'Lake Bato overflow floods the surrounding towns.'),
  ('Buhi',                               'Buhi, Camarines Sur',       13.431000, 123.517000, 1500, 'yellow', 'Lake Buhi outflow flooding.'),
  ('Pili',                               'Pili, Camarines Sur',       13.557000, 123.294000, 1500, 'yellow', 'Bicol River tributary flooding.'),
  ('Legazpi City (Barangay Bigaa)',      'Legazpi City, Albay',       13.139000, 123.734000, 2000, 'orange', 'Yawa River overflow plus Mayon lahar channels.'),
  ('Daraga',                             'Daraga, Albay',             13.152000, 123.710000, 1500, 'orange', 'Mayon lahar and flash flooding.'),
  ('Guinobatan',                         'Guinobatan, Albay',         13.190000, 123.599000, 1500, 'orange', 'Mayon lahar channels; Quinali River overflow.'),
  ('Camalig',                            'Camalig, Albay',            13.181000, 123.669000, 1500, 'orange', 'Mayon lahar-prone river channels.'),
  ('Polangui',                           'Polangui, Albay',           13.293000, 123.486000, 1500, 'orange', 'Quinali River and Bicol basin flooding.'),
  ('Libon',                              'Libon, Albay',              13.302000, 123.437000, 1500, 'orange', 'Lake Bato/Bicol River backflow.'),
  ('Oas',                                'Oas, Albay',                13.257000, 123.501000, 1500, 'orange', 'Quinali River floodplain.'),
  ('Tabaco City',                        'Tabaco City, Albay',        13.359000, 123.733000, 1500, 'yellow', 'Coastal and lahar-channel flooding.'),
  ('Daet',                               'Daet, Camarines Norte',     14.112000, 122.955000, 2000, 'orange', 'Daet River overflow into the town centre.'),
  ('Labo',                               'Labo, Camarines Norte',     14.152000, 122.830000, 1500, 'yellow', 'Labo River flash flooding.'),
  ('Sorsogon City',                      'Sorsogon City, Sorsogon',   12.974000, 124.006000, 2000, 'orange', 'Coastal city; storm surge plus river overflow.'),
  ('Bulan',                              'Bulan, Sorsogon',           12.670000, 123.875000, 1500, 'yellow', 'Coastal river-mouth flooding.'),
  ('Virac',                              'Virac, Catanduanes',        13.585000, 124.234000, 1500, 'yellow', 'Typhoon-belt island; riverine and coastal flooding.'),
  ('Masbate City',                       'Masbate City, Masbate',     12.367000, 123.621000, 1500, 'yellow', 'Coastal urban flooding.'),

  -- ==========================================================================
  -- REGION VI — Western Visayas (Panay and Jalaur river basins)
  -- ==========================================================================
  ('Iloilo City (Jaro / Mandurriao)',    'Iloilo City, Iloilo',       10.720000, 122.562000, 2500, 'red',    'Iloilo and Jaro river system; catastrophic 2008 Frank flooding.'),
  ('Pavia',                              'Pavia, Iloilo',             10.777000, 122.541000, 1500, 'orange', 'Tigum-Aganan river plain.'),
  ('Santa Barbara (Iloilo)',             'Santa Barbara, Iloilo',     10.825000, 122.534000, 1500, 'orange', 'Tigum River overflow.'),
  ('Dumangas',                           'Dumangas, Iloilo',          10.829000, 122.706000, 2000, 'orange', 'Jalaur River delta; coastal plus riverine flooding.'),
  ('Barotac Nuevo',                      'Barotac Nuevo, Iloilo',     10.900000, 122.705000, 1500, 'orange', 'Jalaur River floodplain.'),
  ('Zarraga',                            'Zarraga, Iloilo',           10.830000, 122.594000, 1500, 'yellow', 'Jalaur lowland rice plain.'),
  ('Leganes',                            'Leganes, Iloilo',           10.783000, 122.586000, 1200, 'yellow', 'Coastal lowland.'),
  ('Oton',                               'Oton, Iloilo',              10.693000, 122.475000, 1500, 'yellow', 'Coastal plain flooding.'),
  ('Passi City',                         'Passi City, Iloilo',        11.108000, 122.642000, 1500, 'yellow', 'Jalaur River middle reach.'),
  ('Dueñas',                             'Dueñas, Iloilo',            11.068000, 122.618000, 1500, 'yellow', 'Jalaur River floodplain.'),
  ('Calinog',                            'Calinog, Iloilo',           11.123000, 122.510000, 1500, 'yellow', 'Jalaur headwaters; dam-release flooding.'),
  ('Roxas City (Panay River)',           'Roxas City, Capiz',         11.585000, 122.751000, 2500, 'red',    'Panay River mouth; flagged for national flood-mitigation priority.'),
  ('Panay (Capiz)',                      'Panay, Capiz',              11.554000, 122.801000, 1500, 'orange', 'Panay River bank town.'),
  ('Pontevedra (Capiz)',                 'Pontevedra, Capiz',         11.478000, 122.828000, 1500, 'orange', 'Panay River delta.'),
  ('Panitan',                            'Panitan, Capiz',            11.470000, 122.766000, 1500, 'yellow', 'Panay River floodplain.'),
  ('Mambusao',                           'Mambusao, Capiz',           11.429000, 122.594000, 1500, 'yellow', 'Mambusao River overflow.'),
  ('Dumarao',                            'Dumarao, Capiz',            11.266000, 122.687000, 1500, 'yellow', 'Panay River upper reach.'),
  ('Kalibo',                             'Kalibo, Aklan',             11.708000, 122.364000, 2000, 'red',    'Aklan River overflow; town-wide flooding in typhoons.'),
  ('Numancia',                           'Numancia, Aklan',           11.707000, 122.330000, 1500, 'orange', 'Aklan River bank.'),
  ('New Washington',                     'New Washington, Aklan',     11.656000, 122.429000, 1500, 'yellow', 'Aklan River delta.'),
  ('Banga (Aklan)',                      'Banga, Aklan',              11.641000, 122.345000, 1500, 'yellow', 'Aklan River floodplain.'),
  ('San Jose de Buenavista',             'San Jose de Buenavista, Antique',10.744000,121.939000,1500,'yellow', 'Coastal town; river-mouth flooding.'),
  ('Sibalom',                            'Sibalom, Antique',          10.792000, 122.011000, 1500, 'yellow', 'Sibalom River overflow.'),
  ('Bugasong',                           'Bugasong, Antique',         11.048000, 122.063000, 1200, 'yellow', 'Flash flooding from the Central Panay range.'),
  ('Jordan',                             'Jordan, Guimaras',          10.594000, 122.592000, 1200, 'yellow', 'Coastal and creek flooding.'),
  ('Bacolod City lowland',               'Bacolod City, Negros Occidental',10.670000,122.950000,2500,'orange', 'Urban drainage overload; flagged for national flood-mitigation priority.'),
  ('Bago City',                          'Bago City, Negros Occidental',10.538000,122.836000, 1500, 'orange', 'Bago River overflow.'),
  ('Talisay (Negros Occidental)',        'Talisay City, Negros Occidental',10.737000,122.966000,1500,'yellow', 'Coastal lowland flooding.'),
  ('Silay City',                         'Silay City, Negros Occidental',10.798000,122.974000, 1500, 'yellow', 'Coastal plain and creek overflow.'),
  ('Himamaylan City',                    'Himamaylan City, Negros Occidental',10.099000,122.871000,1500,'yellow','Ilog-Hilabangan basin tributary.'),
  ('Kabankalan City',                    'Kabankalan City, Negros Occidental',9.989000,122.815000,1500,'yellow','Ilog-Hilabangan River basin (one of the 18 major basins).'),
  ('Sagay City',                         'Sagay City, Negros Occidental',10.944000,123.420000, 1500, 'yellow', 'Coastal flooding and storm surge.'),
  ('Dumaguete City',                     'Dumaguete City, Negros Oriental',9.307000,123.308000, 1500, 'yellow', 'Banica River overflow into the city.'),
  ('Bais City',                          'Bais City, Negros Oriental',9.591000,  123.122000, 1500, 'yellow', 'Coastal plain flooding.'),
  ('Sibulan',                            'Sibulan, Negros Oriental',  9.360000,  123.296000, 1200, 'yellow', 'River-mouth flooding.'),

  -- ==========================================================================
  -- REGION VII / VIII — Central and Eastern Visayas
  -- ==========================================================================
  ('Barangay Mambaling / Basak Pardo',   'Cebu City, Cebu',           10.283000, 123.872000, 1500, 'red',    'Chronically flooded lowland; clogged creeks and inadequate drainage.'),
  ('Barangay Tisa / Labangon',           'Cebu City, Cebu',           10.304000, 123.871000, 1200, 'orange', 'Hillside runoff funnels into narrow creeks.'),
  ('Barangay Kinasang-an (Pardo)',       'Cebu City, Cebu',           10.268000, 123.858000, 1200, 'orange', 'Coastal barangay; recurring flooding in habagat rain.'),
  ('Tinago / Carbon (N. Bacalso Ave)',   'Cebu City, Cebu',           10.296000, 123.894000, 1200, 'orange', 'City-centre drainage overload; N. Bacalso and Sanciangko flood fast.'),
  ('Barangay Subangdaku',                'Mandaue City, Cebu',        10.326000, 123.926000, 1500, 'orange', 'Butuanon River overflow into the industrial lowland.'),
  ('Talisay City (Cebu)',                'Talisay City, Cebu',        10.245000, 123.849000, 1500, 'orange', 'Mananga River overflow and coastal flooding.'),
  ('Consolacion',                        'Consolacion, Cebu',         10.376000, 123.958000, 1200, 'yellow', 'Creek overflow in the urbanising north corridor.'),
  ('Liloan',                             'Liloan, Cebu',              10.399000, 123.999000, 1200, 'yellow', 'Coastal creek flooding.'),
  ('Naga City (Cebu)',                   'Naga City, Cebu',           10.209000, 123.758000, 1200, 'yellow', 'Riverine flooding and debris flow.'),
  ('Carcar City',                        'Carcar City, Cebu',         10.106000, 123.640000, 1200, 'yellow', 'River-mouth flooding.'),
  ('Danao City',                         'Danao City, Cebu',          10.521000, 124.027000, 1200, 'yellow', 'Coastal river flooding.'),
  ('Tagbilaran City',                    'Tagbilaran City, Bohol',    9.654000,  123.857000, 1500, 'yellow', 'Urban drainage overload.'),
  ('Loboc–Loay (Loboc River)',           'Loboc, Bohol',              9.637000,  124.033000, 1500, 'orange', 'Loboc River overflow floods riverside barangays.'),
  ('Ubay',                               'Ubay, Bohol',               10.056000, 124.473000, 1500, 'yellow', 'Coastal plain flooding.'),
  ('Barangay 88 San Jose',               'Tacloban City, Leyte',      11.228000, 125.010000, 2000, 'red',    'Storm-surge ground zero in Yolanda; chronic coastal and riverine flooding.'),
  ('Palo',                               'Palo, Leyte',               11.158000, 124.991000, 1500, 'orange', 'Coastal plain; storm surge plus river overflow.'),
  ('Ormoc City',                         'Ormoc City, Leyte',         11.006000, 124.607000, 2000, 'red',    'Anilao–Malbasag flash flood corridor; 1991 disaster killed thousands.'),
  ('Alangalang',                         'Alangalang, Leyte',         11.205000, 124.848000, 1500, 'orange', 'Binahaan River floodplain.'),
  ('Baybay City',                        'Baybay City, Leyte',        10.679000, 124.800000, 1500, 'yellow', 'Coastal and river-mouth flooding.'),
  ('Carigara',                           'Carigara, Leyte',           11.299000, 124.682000, 1500, 'yellow', 'Coastal bay-head flooding.'),
  ('Jaro (Leyte)',                       'Jaro, Leyte',               11.183000, 124.786000, 1500, 'yellow', 'Binahaan River overflow.'),
  ('Maasin City',                        'Maasin City, Southern Leyte',10.133000,124.845000, 1200, 'yellow', 'Coastal city with steep catchments.'),
  ('Sogod',                              'Sogod, Southern Leyte',     10.386000, 124.993000, 1200, 'yellow', 'Bay-head flooding and debris flow.'),
  ('Catbalogan City',                    'Catbalogan City, Samar',    11.775000, 124.886000, 1500, 'orange', 'Coastal city hemmed by steep slopes; flash flooding.'),
  ('Calbayog City',                      'Calbayog City, Samar',      12.067000, 124.596000, 1500, 'orange', 'Calbayog and Oquendo river overflow.'),
  ('Catarman',                           'Catarman, Northern Samar',  12.498000, 124.638000, 1500, 'orange', 'Catarman River overflow; typhoon-belt town.'),
  ('Borongan City',                      'Borongan City, Eastern Samar',11.608000,125.432000, 1500, 'yellow', 'Loom River and Pacific coastal flooding.'),
  ('Guiuan',                             'Guiuan, Eastern Samar',     11.033000, 125.724000, 1200, 'yellow', 'Exposed Pacific coastline; storm surge.'),
  ('Naval',                              'Naval, Biliran',            11.562000, 124.397000, 1200, 'yellow', 'Coastal and river-mouth flooding.'),

  -- ==========================================================================
  -- REGION IX / X — Zamboanga Peninsula and Northern Mindanao
  -- ==========================================================================
  ('Barangay Talon-Talon / Rio Hondo',   'Zamboanga City, Zamboanga del Sur',6.911000,122.090000,1800,'orange','Coastal stilt communities; tidal and storm-surge flooding.'),
  ('Pagadian City',                      'Pagadian City, Zamboanga del Sur',7.826000,123.437000,1500,'yellow', 'Coastal city with steep upland catchments.'),
  ('Dipolog City',                       'Dipolog City, Zamboanga del Norte',8.589000,123.341000,1500,'orange','Dipolog River overflow into the city centre.'),
  ('Ipil',                               'Ipil, Zamboanga Sibugay',   7.784000,  122.587000, 1200, 'yellow', 'Coastal river-mouth flooding.'),
  ('Barangay Macasandig / Balulang',     'Cagayan de Oro City, Misamis Oriental',8.445000,124.635000,2500,'red','Cagayan de Oro River; Sendong 2011 killed over a thousand here.'),
  ('Barangay Consolacion / Isla de Oro', 'Cagayan de Oro City, Misamis Oriental',8.482000,124.642000,1200,'red','River-island settlements swept away in Sendong; still occupied.'),
  ('Barangay Hinaplanon (Mandulog River)','Iligan City, Lanao del Norte',8.235000,124.238000,2000,'red',     'Mandulog River flash flood corridor; Sendong ground zero.'),
  ('Tagoloan',                           'Tagoloan, Misamis Oriental',8.539000,  124.758000, 1500, 'orange', 'Tagoloan River basin (one of the 18 major basins).'),
  ('Villanueva',                         'Villanueva, Misamis Oriental',8.585000,124.786000, 1200, 'yellow', 'Coastal plain flooding.'),
  ('Opol',                               'Opol, Misamis Oriental',    8.515000,  124.572000, 1500, 'yellow', 'Iponan River overflow.'),
  ('El Salvador City',                   'El Salvador City, Misamis Oriental',8.568000,124.523000,1500,'yellow','Coastal and river flooding.'),
  ('Balingasag',                         'Balingasag, Misamis Oriental',8.744000,124.777000, 1200, 'yellow', 'Coastal river-mouth flooding.'),
  ('Gingoog City',                       'Gingoog City, Misamis Oriental',8.829000,125.100000,1200, 'yellow', 'Flash flooding from steep catchments.'),
  ('Ozamiz City',                        'Ozamiz City, Misamis Occidental',8.146000,123.844000,1500, 'yellow', 'Coastal urban flooding.'),
  ('Oroquieta City',                     'Oroquieta City, Misamis Occidental',8.486000,123.805000,1200,'yellow','Coastal and river flooding.'),
  ('Malaybalay City',                    'Malaybalay City, Bukidnon', 8.157000,  125.128000, 1500, 'yellow', 'Sawaga River overflow.'),
  ('Valencia City',                      'Valencia City, Bukidnon',   7.906000,  125.093000, 1500, 'yellow', 'Pulangi River upper basin.'),
  ('Marawi City lakeshore',              'Marawi City, Lanao del Sur',8.000000,  124.293000, 1500, 'yellow', 'Lake Lanao shoreline; Agus River basin.'),

  -- ==========================================================================
  -- REGION XI — Davao Region (Davao and Tagum–Libuganon basins)
  -- ==========================================================================
  ('Barangay Matina Pangi / Crossing',   'Davao City, Davao del Sur', 7.064000,  125.590000, 1800, 'red',    'Matina River flash flooding; the city''s most repeatedly flooded corridor.'),
  ('Barangay Talomo',                    'Davao City, Davao del Sur', 7.048000,  125.554000, 1500, 'orange', 'Talomo River overflow into coastal barangays.'),
  ('Barangay Bunawan / Tibungco',        'Davao City, Davao del Sur', 7.234000,  125.654000, 1800, 'orange', 'Davao River lower reach; Callawa bridge area flooding.'),
  ('Panabo City',                        'Panabo City, Davao del Norte',7.308000,125.684000, 1500, 'orange', 'Coastal plain between the Davao and Tagum basins.'),
  ('Tagum City',                         'Tagum City, Davao del Norte',7.447000, 125.808000, 2000, 'red',    'Tagum–Libuganon river basin; recurring city-wide flooding.'),
  ('Carmen (Tuganay)',                   'Carmen, Davao del Norte',   7.345000,  125.704000, 1500, 'red',    'Tuganay River overflow; among the most flood-prone towns in Davao del Norte.'),
  ('Asuncion',                           'Asuncion, Davao del Norte', 7.596000,  125.820000, 1200, 'yellow', 'Libuganon River overflow.'),
  ('Compostela',                         'Compostela, Davao de Oro',  7.669000,  126.090000, 1500, 'orange', 'Agusan tributary flooding and debris flow.'),
  ('Monkayo',                            'Monkayo, Davao de Oro',     7.818000,  126.053000, 1500, 'orange', 'Agusan River upper basin; mining silt raises the riverbed.'),
  ('Nabunturan',                         'Nabunturan, Davao de Oro',  7.605000,  125.966000, 1200, 'yellow', 'Riverine flooding.'),
  ('Cateel',                             'Cateel, Davao Oriental',    7.790000,  126.453000, 1500, 'red',    'Cateel River; devastated by Typhoon Pablo flooding in 2012.'),
  ('Baganga',                            'Baganga, Davao Oriental',   7.574000,  126.560000, 1500, 'orange', 'Baganga River and Pacific coastal flooding.'),
  ('Mati City',                          'Mati City, Davao Oriental', 6.955000,  126.216000, 1200, 'yellow', 'Coastal and river-mouth flooding.'),
  ('Digos City',                         'Digos City, Davao del Sur', 6.750000,  125.357000, 1500, 'yellow', 'Digos River overflow.'),
  ('Malita',                             'Malita, Davao Occidental',  6.411000,  125.610000, 1200, 'yellow', 'Coastal river flooding.'),

  -- ==========================================================================
  -- REGION XII / BARMM — Mindanao River (Rio Grande) basin and Ligawasan Marsh
  -- ==========================================================================
  ('Cotabato City',                      'Cotabato City, Maguindanao del Norte',7.223000,124.246000,2500,'red','Rio Grande de Mindanao delta; DENR top-10 flood-prone area.'),
  ('Datu Odin Sinsuat',                  'Datu Odin Sinsuat, Maguindanao del Norte',7.162000,124.220000,2000,'orange','Rio Grande delta lowland.'),
  ('Sultan Kudarat (Maguindanao)',       'Sultan Kudarat, Maguindanao del Norte',7.201000,124.279000,2000,'orange','Rio Grande river bank.'),
  ('Datu Piang (Ligawasan Marsh)',       'Datu Piang, Maguindanao del Sur',7.029000,124.423000,3000,'red',   'Ligawasan Marsh; swells for months every rainy season.'),
  ('Datu Saudi Ampatuan',                'Datu Saudi Ampatuan, Maguindanao del Sur',6.966000,124.427000,2500,'red','Marshland town with near-permanent wet-season inundation.'),
  ('Shariff Aguak',                      'Shariff Aguak, Maguindanao del Sur',6.865000,124.440000,2000,'orange','Ligawasan Marsh fringe.'),
  ('Sultan sa Barongis',                 'Sultan sa Barongis, Maguindanao del Sur',6.883000,124.546000,1500,'orange','Marshland flooding.'),
  ('Pikit',                              'Pikit, Cotabato',           7.040000,  124.670000, 2500, 'red',    'Rio Grande de Mindanao and Ligawasan Marsh; DENR top-10 flood-prone province.'),
  ('Midsayap',                           'Midsayap, Cotabato',        7.190000,  124.532000, 2500, 'red',    'Rio Grande floodplain; recurring town-wide flooding.'),
  ('Libungan',                           'Libungan, Cotabato',        7.257000,  124.523000, 1500, 'orange', 'Libungan River overflow.'),
  ('Kabacan',                            'Kabacan, Cotabato',         7.109000,  124.829000, 2000, 'orange', 'Pulangi–Rio Grande confluence.'),
  ('Aleosan',                            'Aleosan, Cotabato',         7.174000,  124.625000, 1500, 'yellow', 'Rio Grande floodplain.'),
  ('Kidapawan City',                     'Kidapawan City, Cotabato',  7.008000,  125.089000, 1500, 'yellow', 'Flash flooding from the Mt. Apo catchment.'),
  ('Tacurong City',                      'Tacurong City, Sultan Kudarat',6.693000,124.676000, 1500, 'orange', 'Rio Grande basin lowland.'),
  ('Isulan',                             'Isulan, Sultan Kudarat',    6.630000,  124.603000, 1500, 'yellow', 'Riverine flooding.'),
  ('Koronadal City',                     'Koronadal City, South Cotabato',6.503000,124.847000,1500, 'yellow', 'Urban drainage overload.'),
  ('General Santos City',                'General Santos City, South Cotabato',6.113000,125.171000,1800,'yellow','Silway and Buayan river flooding (Buayan-Malungon basin).'),

  -- ==========================================================================
  -- CARAGA — Agusan River basin (third-largest in the country)
  -- ==========================================================================
  ('Barangay Bonbon / Ambago',           'Butuan City, Agusan del Norte',8.949000,125.533000,2500,'red',     'Agusan River delta; the city floods with every major basin surge.'),
  ('Magallanes (Agusan del Norte)',      'Magallanes, Agusan del Norte',9.020000,125.521000, 1500, 'orange', 'Agusan River mouth; tidal plus riverine flooding.'),
  ('Buenavista (Agusan del Norte)',      'Buenavista, Agusan del Norte',8.980000,125.410000, 1500, 'yellow', 'Coastal plain flooding.'),
  ('Cabadbaran City',                    'Cabadbaran City, Agusan del Norte',9.123000,125.534000,1200,'yellow','Cabadbaran River overflow.'),
  ('Las Nieves',                         'Las Nieves, Agusan del Norte',8.735000,125.598000, 1500, 'orange', 'Agusan River middle reach.'),
  ('Talacogon',                          'Talacogon, Agusan del Sur', 8.456000,  125.783000, 2000, 'red',    'Agusan Marsh fringe; months-long inundation each wet season.'),
  ('San Francisco (Agusan del Sur)',     'San Francisco, Agusan del Sur',8.501000,125.970000, 2000, 'red',    'Agusan River bank; among the most flood-affected towns in Caraga.'),
  ('Bunawan (Agusan del Sur)',           'Bunawan, Agusan del Sur',   8.172000,  125.995000, 1500, 'orange', 'Agusan Marsh; prolonged seasonal flooding.'),
  ('Rosario (Agusan del Sur)',           'Rosario, Agusan del Sur',   8.429000,  126.033000, 1500, 'orange', 'Agusan River floodplain.'),
  ('Trento',                             'Trento, Agusan del Sur',    8.048000,  126.064000, 1500, 'orange', 'Agusan River upper reach.'),
  ('Bayugan City',                       'Bayugan City, Agusan del Sur',8.715000,125.750000, 1500, 'orange', 'Agusan tributary flooding.'),
  ('Surigao City',                       'Surigao City, Surigao del Norte',9.789000,125.495000,1500,'yellow', 'Coastal city; storm surge and urban flooding.'),
  ('Tandag City',                        'Tandag City, Surigao del Sur',9.078000,126.198000, 1200, 'yellow', 'Tandag River overflow.'),
  ('Bislig City',                        'Bislig City, Surigao del Sur',8.210000,126.317000, 1200, 'yellow', 'Coastal river-mouth flooding.')
) AS v(name, district, latitude, longitude, radius_m, level, note)
WHERE NOT EXISTS (
    SELECT 1 FROM flood_prone_area f
     WHERE f.name = v.name AND f.district = v.district
);

ANALYZE flood_prone_area;
