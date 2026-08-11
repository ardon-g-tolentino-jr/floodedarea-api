# Changelog

All notable changes to FloodedArea (baha-ui + baha-api), most recent first.

---

## [070-feat-flood-prone-seed-ph] — 2026-08-11

**Summary:** Nationwide reference data for `flood_prone_area`. The table shipped in
054-feat-flood-prone-zones with no seed, so every deployment started empty and an admin had to pin
each zone by hand. This branch adds a 399-row curated seed covering Luzon, Visayas and Mindanao —
MMDA's flood-prone street list for NCR, the DENR–MGB top-ten flood-prone provinces, the PAGASA
18 major river basins, and towns with documented recurring inundation. Data-only: no schema
change, no Java change, no frontend change.

### Frontend
- None. The existing `GET /flood-prone` map bubbles and Admin > Flood-Prone Zones UI render the
  seeded rows with no code change.

### Backend
- None.

### Database
- setup.sql appended: **no** (no schema change — `flood_prone_area` already exists)
- Migration: `db/migrations/feat/flood-prone-seed-ph/20260811_flood_prone_seed_ph.up.sql`
- Rollback:  `db/migrations/feat/flood-prone-seed-ph/20260811_flood_prone_seed_ph.down.sql`
- Depends on `db/migrations/feat/flood-prone-zones/20260805_flood_prone_zones.up.sql` having run.
- Idempotency key is `(name, district)` via `WHERE NOT EXISTS`, so re-running never duplicates a
  zone and never clobbers admin edits to `radius_m` / `level` / `note`.
- Rollback deletes by the same `(name, district)` pairs. Admin-created zones outside the seed list
  are untouched; `flood_alert.area_id` is `ON DELETE SET NULL`, so alerts issued against a deleted
  zone survive as global alerts.
- Row shape: 399 rows, `level` split by how often the area actually floods (red = nearly every
  significant rain event, orange = every heavy monsoon or typhoon, yellow = extreme events only).
  `radius_m` is scaled to the zone: street ≈ 500–1,200 m, town ≈ 1,500–3,000 m, delta/marsh
  ≈ 3,000–6,000 m (Candaba Swamp is the widest at 6,000 m).

**Caveat on the data:** latitude/longitude are approximate centroids of the named place, not
surveyed hazard-polygon boundaries, and the pin+radius model is a circle over what is really an
irregular floodplain. This is an advisory starting set for admins to refine through the Admin UI,
not an authoritative hazard map — UP NOAH and DPWH GeoRiskPH remain the reference for actual
susceptibility. Sources are listed in the header comment of the migration.

### Config / Env
- None.

### Testing
- All 399 rows machine-validated before commit: every row parses to exactly 7 fields; every
  latitude falls in 4.0–21.5 °N and longitude in 116.0–127.0 °E (Philippine bounding box); every
  `radius_m` satisfies `flood_prone_area_radius_check` (50–20,000); every `level` is a valid
  `alert_level_t`; zero duplicate `(name, district)` pairs. Parentheses and quote escaping balance
  in both up and down scripts.
- **Not yet applied to a live database** — no local Postgres credentials were available in the
  session that wrote it. Apply and verify with:
  `psql "$DB_URL" -f db/migrations/feat/flood-prone-seed-ph/20260811_flood_prone_seed_ph.up.sql`
  then `SELECT count(*), level FROM baha.flood_prone_area GROUP BY level;` and re-run the up
  script once to confirm the count does not change.

---

## [069-fix-ui-defect-batch] — 2026-08-11

**Summary:** Five unrelated defects reported from live use of v1.58.0, shipped as one fix branch.
Three of them had the same shape — a control that *looked* correct in isolation but was never
checked against the thing it interacts with: a floating button z-indexed against a sibling it can't
actually compete with, a backdate field validated independently of the lifetime it consumes, and a
"Nearby" list whose radius control drew a circle but filtered nothing. Frontend-only; no API,
schema, or config change.

### Frontend
- **Report FAB no longer covers the detail card's buttons.** On mobile the "+ Report flood" FAB and
  the floating trip chip are root-level siblings of the map area, which is its own
  `isolation: isolate` stacking context — so they paint above *everything* inside it, including the
  detail sheet at z-index 1110. This is a sibling-context problem, not an ordering one, so no
  z-index change can fix it; both are now gated on `!selectedReport && !selEvac` (the same values
  the two detail cards are gated on) and simply don't render while a sheet is open. Share Report /
  Confirm / **Remove my report** were the buttons being buried. Also gave the *desktop* detail card
  `maxHeight: calc(100vh - 148px)` + `overflowY: auto` — it had neither, so a tall card ran off a
  short viewport with no way to reach its bottom actions.
- **Admin "Hours ago" can no longer post an already-expired report.** `REPORT_TTL_MIN` is
  `{low: 20, medium: 45, high: 90}` — **minutes**, not hours — so "6 hours ago" on the default TTL
  produced a report that was dead on arrival (`expiresInMin = max(0, ttl - age)` → 0), invisible on
  the map the moment it landed. The input had `min={0}`, no `max`, and was never compared against
  "Keep live for". The two fields are now validated against each other: the input carries a live
  `max`, clamps on entry, shows the remaining lifetime ("Would stay live for 6h") or a red warning
  naming the window, and **Post is disabled** while the backdate exceeds it.
- **Flood-prone zones stop accumulating duplicates.** "⚠ Also mark this place as flood-prone"
  unconditionally `POST`ed a new zone; nothing in the codebase mapped a `(lat,lng)` back to an
  existing `FloodProneArea`, so every incident logged at an already-known place minted another row.
  New pure helper `viewModel.matchingProneZone(zones, loc, excludeId?)` returns the nearest zone
  whose circle (or a `ZONE_MATCH_MIN_M = 150` floor) contains the pin. Wired into three places: the
  incident modal replaces the checkbox with a read-only "✓ Already flood-prone: {name}" chip;
  `App.onReportIncident` re-checks against the freshest list before POSTing (the modal's snapshot
  can be stale); and Admin → Zones refuses a duplicate with an inline error naming the match. A
  matched zone is deliberately **never** PATCHed — its level/note/radius are admin-curated. The
  Zones guard is scoped to creates and edits that *move* the pin, so the duplicates that already
  exist in the data stay editable rather than being cleanable only by delete.
- **Filters chip agrees with its count.** `StripChip` rendered a fixed plural label beside a bare
  number ("Filters 1"). New `sidebar.panelFilterOne` key; 0 → "Filters", 1 → "Filter 1",
  2 → "Filters 2". Same singular/plural idiom already used by `trip.reportsOne`/`reportsMany`. The
  neighbouring "N high · N active · N impassable" strings are adjectives and were already
  count-invariant.
- **"Nearby" now means nearby.** The list was `visible` *sorted* by distance with no cutoff, so a
  report 47.5 km away in Calumpit sat under Nearby while the safety banner directly above it read
  "no reports within 2.5 km". It is now cut to `monitorRadiusKm` — the existing "My area" slider
  (default 3 km, range 1–20), which until now was purely cosmetic: it drew the dashed map circle and
  labelled a chip but filtered nothing. The selected report is kept regardless of distance so a
  `?report=<id>` deep link or a marker tap never selects a row that isn't in the list. `visible`
  itself is **not** filtered — the map still pins every live report, and Live activity / Archive are
  untouched, so distant reports stay discoverable. New `sidebar.noneInRadius` empty state names the
  radius and points at the control, distinct from the existing "no reports match the active filters".
- Version: 1.58.0 → 1.58.1 (all five are defects → patch bump).

### Backend
- None.

### Database
- setup.sql appended: no
- Migration: none
- Rollback:  none

### Config / Env
- None.

### Testing
- `npm run typecheck` clean; `npm run build` clean.
- Verified in-browser against `npm run dev:mock` at a 500×667 mobile viewport (mock reports
  re-stamped to now, since the seed data is timestamped June 2026 and expires instantly):
  - Opening a report sheet hides the FAB and trip chip; Share Report and Confirm are fully
    reachable; both controls return on close.
  - Default TTL (high, 90 min): typing `6` clamps to `1`, hint reads "Would stay live for 30m".
    TTL → 12 hours: `max` becomes 11, `6` accepted, "Would stay live for 6h". Dropping TTL back to
    Default with 6 still set → red "Older than the 1h 30m window…" and Post disabled.
  - Pin dropped inside a seeded zone → checkbox replaced by "✓ Already flood-prone: {name}";
    posting created the report (13 rows) with the zone count unchanged at 4. Admin → Zones re-add at
    the same searched place → refused, naming the existing zone. Two zones at identical coordinates:
    renaming one still saves (edit path deliberately not blocked).
  - Filters chip: 0 → "Filters", 1 → "Filter 1", 2 → "Filters 2".
  - Nearby dropped from 10 rows to the 3 within 3 km while the summary still read "10 active" and
    the map kept every pin; at 1 km the empty state read "No reports within 1 km. Widen "My area"
    to see more."
- `mock-api/db.json` restored to its seed state after testing.

---

## [068-feat-flood-prone-reports-and-bubbles] — 2026-08-10

**Summary:** Flood-prone zones were nearly inert — a faint dashed circle with a hover-only tooltip,
and "⚠ Warn" (a broadcast alert) as their only action. So when a storm hit, an admin who already
knew the ten places that flood had to re-enter every one by hand through the incident modal. Zones
can now be promoted to live reports in bulk, they carry a readable Google-Maps-style callout on the
map that escalates when water is actually there, and the two things that made admin pinning slow —
the incident modal opening on the Intersection tab, and search results that couldn't tell two
same-named streets apart — are fixed. Frontend-only; no API or schema change.

### Frontend
- **Zone → live report.** New `ZoneReportModal.tsx`: checkbox list of active zones (all ticked),
  one shared severity/depth/TTL for the batch, "Post N reports". Reached from a
  `⚡ Report flooding on zones…` button on Admin → Zones, or per zone from a row's `⚡ Report now`
  and from a map bubble tap. `App.reportZones` posts sequentially as `system: true` reports and
  reports a partial tally (`Posted N of M`) rather than failing all-or-nothing.
- Duplicate guard: a zone with an active report within 150 m (`kmBetween`) renders disabled and
  unticked as "Already reported nearby", so a second pass during the same event can't double-post.
- Deliberately **no** Facebook/X auto-post on the batch — N zones would mean N separate posts. The
  success toast points at the consolidated flood digest (066) instead.
- **Map bubbles.** Each active zone gets a permanent callout pill above its circle: level-colored
  `⚠ {name} / Flood-prone` when quiet, red + pulsing `🔴 {name} / Flooded now · N cm` when a live
  report sits inside its radius (`viewModel.zonesWithStatus`, pure). Gated to zoom ≥ 13
  (`ZONE_LABEL_MIN_ZOOM`) so a zoomed-out Metro Manila view stays readable, and drawn in a custom
  Leaflet pane at z-index 399 so a bubble never paints over a live report pin.
- Wired the previously-unused `showFloodProne` prop to a new **Flood-prone** chip in the sidebar's
  SHOW ON MAP strip (hides circles and bubbles together; counted in the Filters badge).
- **Admin "Pick on map" is now the default** location tab in the incident modal, and its mini-map
  renders immediately — centered on the admin's location, with a "Tap the map to drop the pin" hint
  (`MapPickField` gained `pinned`). Previously the map only appeared *after* a search had already
  resolved coordinates, so the tab named "Pick on map" could not be used to pick on a map.
- **Search results carry barangay/city.** `searchPlaces` now requests `addressdetails=1`
  (limit 6 → 8) and returns `primary` + `context` alongside the unchanged `label`; the shared
  `pickCity`/`pickBarangay` helpers are now used by both `searchPlaces` and `reverseGeocode`. New
  `PlaceSuggestion.tsx` renders a bold name over a muted `barangay · city · province` line and
  replaces three near-identical inline implementations (`SearchBox`, `PlaceField`,
  `PublicDashboardModal`). "Rizal Street" now resolves visibly to Binangonan vs. Bacoor vs. Taguig
  instead of four identical truncated rows. `PlaceField` also gained the `near` bias `SearchBox`
  already had.
- Fixes found while verifying: zone rows in Admin → Zones now wrap (a fourth action button had
  squeezed the zone name to an ellipsis); `AdminWarnZoneModal` and `ZoneReportModal` moved to
  z-index 1300 — both are opened *from* the admin console, which sits at the same 1200 and renders
  later in `App`'s JSX, so it won the tie and buried them. **The existing "⚠ Warn" button was
  affected by this too and appeared inert.**
- Version: 1.57.0 → 1.58.0.

### Backend
- None. `POST /reports {system:true}` and `GET /flood-prone` already existed.

### Database
- setup.sql appended: no
- Migration: none
- Rollback:  none

### Config / Env
- None.

### Testing
- `npm run typecheck` clean; `npm run build` clean.
- Verified against `npm run demo` (json-server mock, 3 seeded zones): bulk post of 3 zones →
  `Posted 3 of 3`, pins on the map, bubbles flipped to red "Flooded now · 108 cm", status card moved
  to STAY ALERT; reopening the modal showed all 3 as "Already reported nearby" with Post disabled.
  Zoom 13 → bubbles shown, zoom 12 → circles only. Flood-prone chip hid both layers. Incident modal
  opened on Pick on map with a live map; one click filled Name "Tanguile Street" + District. Search
  for "Rizal Street" returned four distinct barangay/city-qualified rows.
- `mock-api/db.json` restored to its seed state after testing.

---

## [067-feat-sidebar-controls-strip] — 2026-08-10

**Summary:** Reclaims the sidebar for the report list. Everything above the Nearby / Live activity /
Archive tabs was `flex: none`, so the list — the only flexible child — was squeezed to ~145 px of an
850 px sidebar (under 20 %), and that was with all four config sections *already collapsed*: ~160 px
went to empty section headers alone and the always-expanded alerts block cost more than all four
collapsed sections combined. The five stacked sections are now one compact accordion chip strip, and
the list is given a structural 50 % floor.

### Frontend
- `Sidebar.tsx`: five stacked `CollapsibleSection`s (Trip / Filters / Roads / Affected area /
  Monitoring area) replaced by a single ~40 px chip strip — **Trip · Filters · My area** — with at
  most one body open at a time. Defaults to all closed; the open panel persists to
  `baha_sidebar_panel_v1` (replaces `baha_sidebar_sections_v3`, which is simply no longer read).
- Regrouped, with no control changed or removed: *Filters* now holds the severity chips, the
  Shelters / Flooded-roads layer toggles (`SHOW ON MAP`) and the road-condition chips plus Search
  roads (`ROAD CONDITION`); *My area* holds the monitoring-radius slider, the water-rise projection
  slider and the Nearby flood alerts button, which are all radius-scoped.
- Chip badges surface what the collapsed group hides: Trip shows `⚠ n` / `✓` while a trip is
  running, Filters shows how many of the eight map toggles are currently hiding something, My area
  shows the monitoring radius (or `+N cm` when a projection is active).
- Safety-status card trimmed: dropped the redundant `YOUR SAFETY STATUS` label (the badge says it),
  tighter padding, and the grey advisory sub-line now renders only when the tone is WARN or DANGER.
- Summary counts shortened to one line (`● 1 high · 1 active · ▬ 1 impassable`) — it previously
  wrapped to two or three lines at 386 px.
- Layout: controls are now the only shrinkable child (`flex: 0 1 auto`, scrolls internally) and the
  list takes `flex: 1 0 50%`, so with panels closed it is guaranteed half the sidebar. While a panel
  is open the list yields to a 168 px floor so the opened panel gets real room. Mobile is unchanged —
  still one column in normal flow, no nested scroller.
- `TripPanel.tsx` returns its body only; the strip owns the heading and open state (drops
  `baha_trip_open_v1`). The mobile trip sheet in `App.tsx` supplies its own heading and padding.
- `CollapsibleSection.tsx` deleted — no remaining importers.
- Measured on an 827 px sidebar: list went from ~145 px (18 %) to 425 px (51 %).

### Backend
- None.

### Database
- setup.sql appended: no
- Migration: none
- Rollback:  none

### Config / Env
- None. UI version 1.56.0 → 1.57.0.

### Testing
- `npm run typecheck` clean; `npm run build` clean.
- Verified in Chrome against the json-server mock: closed-state split measured at 51 % list; each
  chip opens in isolation and `aria-expanded` tracks it; open panel restored from
  `baha_sidebar_panel_v1` after reload; sliders, layer toggles, Search roads and the alerts button
  all still drive the map and modals; Trip chip badge confirmed in both `✓` and `⚠ 1` states; a
  freshly posted nearby high report flipped the status card to STAY ALERT and brought the advisory
  sub-line back; mobile branch confirmed to have no nested scrollers.

## [066-feat-admin-flood-digest] — 2026-08-10

**Summary:** Adds an admin-only **Flood digest** — one consolidated social post covering *every*
currently-active flood report, grouped by city, with each report's time, severity, depth, trend and
road status. Until now the only way to post a flood situation was one report at a time through the
per-report auto-post ticked in the incident modal, which during a real event means a dozen separate
posts instead of a single situational roundup. A dedicated 📣 button in the TopBar (ADMIN only)
opens a preview modal that hands the digest to the Facebook and X composers.

Publishing is **client-side by design** — this branch deliberately does not touch
`SocialPublishService` or the `social_post` table, so nothing here can affect the existing
server-side auto-post pipeline or its kill switches.

Two consequences of the client-side choice, recorded so they aren't rediscovered later:
- Facebook's `sharer.php` accepts **a URL only** (the `quote` param was deprecated in 2017), so the
  Facebook button copies the digest to the clipboard and opens the composer for the admin to paste.
  X's `intent/tweet` does accept prefilled text, so X is one click.
- A client-side share posts as whoever is signed into Facebook in that browser, not automatically as
  the FloodedArea Page. Posting *as the Page* with real text and no pasting would require a new
  server-side digest endpoint on top of `SocialPublishService` — not done here.

### Frontend
- `src/utils/share.ts`: new `buildFloodDigest(reports, now, url): FloodDigest` plus the private
  `groupByCity` / `digestReportLine` / `buildDigestXBody` helpers. Pure per the `utils/` convention —
  `now` and `url` are passed in. Groups by `localityParts(district).city` (`FloodReport` carries no
  `city` field), orders cities and reports worst-first by `SEV_RANK` then depth, and caps the long
  form at 6 cities / 4 reports per city with "…and N more" lines. Emits a second X-sized variant
  (per-city tally + worst report) trimmed to the 280-char budget by dropping trailing cities, then
  the "Worst:" line, then hard-truncating — so the string sent to the intent is never over budget.
  `X_URL_COST` (23) and `X_LIMIT` (280) were lifted here from `AdminReportIncidentModal` so there is
  one copy; that modal now imports them.
- `src/components/panels/FloodDigestModal.tsx` (new): preview dialog modelled on `ShareModal`
  (bottom sheet on mobile, card on desktop). Editable textarea seeded from the digest — it follows
  the 15 s recompute until the admin types, then stops so a background refresh can't eat an edit.
  Read-only X preview with a live `n/280` counter, three actions (Copy & open Facebook / Post to X /
  Copy text), and a disabled empty state when nothing is active.
- `src/App.tsx`: `digestOpen` state, the `floodDigest` memo over the existing `active` list, the
  `postDigestFacebook` / `postDigestX` handlers (clipboard + `window.open`, side effects kept out of
  `utils/`), and the ADMIN-gated modal render.
- `src/components/panels/TopBar.tsx`: sky-tinted admin-only `digestBtn` beside the existing Response
  and Admin buttons; mirrored in the mobile overflow menu. New `onFloodDigest` prop.
- `src/i18n.ts`: `topbar.floodDigest` (en/fil).
- Version 1.55.0 → **1.56.0**; `dist/` rebuilt and committed.

### Backend
- None.

### Database
- setup.sql appended: no
- Migration: none (no schema change)
- Rollback:  none

### Config / Env
- None. No new variables; the `SOCIAL_*` / `FB_*` / `X_*` settings from `065` are not involved —
  the digest never reaches the server.

### Testing
- `npm run typecheck` clean; `npm run build` succeeds.
- `buildFloodDigest` executed directly against synthetic reports for: empty, single report, the
  3-city typical case, the cap case (8 cities with 6 reports in one → "…and 2 more in this area" /
  "…and 2 more cities"), a 24-report/12-city X stress case (trimmed to 276/280, never over), and a
  report with `reportedAt` set (confirms the source time wins over `createdAt`).
- Manual: admin-only visibility of the TopBar button, live drop-out of reports as their TTL expires,
  and both composers opening with the expected contents.

---

## [065-feat-social-autopost-enable] — 2026-08-10

**Summary:** The admin auto-post pipeline shipped in `feat-social-auto-post` has never actually
published anything, and not because of a code gap: `baha.social.enabled` defaults to `false`,
`baha.social.dry-run` to `true`, and `docker-compose.yml` passed **none** of the ten social
variables into the `api` container — so the Facebook/X credentials had no delivery path in a
containerised deploy and every ticked post landed as `skipped`. This branch plumbs those variables
through, documents the tokens and their scopes, and writes down the rollout order. No behaviour
changes on its own: every default stays at the safe value, so publishing still has to be switched
on deliberately.

Scope decision: auto-post stays **admin-only**. `FloodReportService`'s `system && role == ADMIN`
gate is untouched, so citizen reports continue to ignore `socialNetworks` even if a crafted client
sends them.

### Frontend
- None. No baha-ui source, version, or `dist/` change — the citizen report flow, `ShareModal`, and
  the share intent buttons are all untouched.

### Backend
- No Java change. `FloodReportService`, `SocialPublishService`, `FacebookPoster`, and `XPoster` are
  as shipped in `feat-social-auto-post`.
- `docker-compose.yml`: the `api` service now forwards `SOCIAL_ENABLED`, `SOCIAL_DRY_RUN`,
  `SOCIAL_MAX_ATTEMPTS`, `SOCIAL_API_BASE_URL`, `FB_PAGE_ID`, `FB_PAGE_TOKEN`, `FB_GRAPH_VERSION`,
  `X_API_KEY`, `X_API_SECRET`, `X_ACCESS_TOKEN`, `X_ACCESS_SECRET`, each with a `${VAR:-default}`
  fallback matching `application.properties` — an unset variable keeps today's off-by-default
  behaviour.
- `README.md`: new "Social auto-post" section covering the admin-only gate, the two independent
  control layers (env master switch vs. the live `app_setting` per-network kill switches), the
  required token scopes, and the dry-run-first rollout order. Added the `/social-posts` and
  `/social-posts/{id}/retry` admin rows to the endpoint table, and closed a stray code fence that
  was swallowing the end of that table.

### Database
- setup.sql appended: no — `app_setting` already seeds `social_autopost_facebook` and
  `social_autopost_x`, and this branch adds no schema.
- Migration: none
- Rollback:  none

### Config / Env
- `.env.example`: added the ten social variables plus `BAHA_PUBLIC_BASE_URL`, each with a comment.
  Values are placeholders only; `.env` remains gitignored and no real token is committed.
- **`SOCIAL_API_BASE_URL` is the public base URL of the API, not the site.** `FacebookPoster`
  builds the photo URL as `<SOCIAL_API_BASE_URL><photoUrl>`, so a blank value fails every Facebook
  post permanently with "baha.social.api-base-url not configured". Under the standard nginx deploy
  the correct value is `https://floodedarea.com/api` (nginx strips the `/api/` prefix upstream).
- Still required before anything publishes: a long-lived Facebook Page token with
  `pages_manage_posts` + `pages_read_engagement`, and an X app set to Read and Write.

### Testing
- `docker compose exec api env | grep -E 'SOCIAL|FB_|X_'` — the variables now reach the container;
  before this branch the grep returned nothing.
- Dry run (`SOCIAL_ENABLED=true`, `SOCIAL_DRY_RUN=true`): post a system report as an admin with both
  networks ticked → two `social_post` rows, status `skipped`, `lastError` "Dry run — payload logged",
  caption visible in the Admin console and in the `[social] dry-run …` log line.
- Live (`SOCIAL_DRY_RUN=false`): same post → rows flip to `sent` with a populated `externalUrl`.
- Failure path: blank `X_ACCESS_TOKEN` → `failed` row carrying `lastError`, re-queued by Retry.
- Kill switch: Facebook off in the Admin console → only the X row is created; no restart needed.
- Citizen report → no `social_post` rows, confirming the admin-only gate.

---

## [feat-social-auto-post] — 2026-08-06

**Summary:** Admin "system" flood reports (news/other-source incidents logged via the incident
modal) previously reached the FloodedArea Facebook Page and X account only if a human clicked
through the per-report ShareModal. baha-api now publishes the report itself — server-side,
asynchronously, after the create transaction commits — the moment an admin asks for it, tracking
every attempt in a new `social_post` table. This entry covers the backend half only; the frontend
half (per-report toggle, caption preview, AdminPanel kill switches + retry list) ships in
baha-ui branch `059-feat-social-auto-post`.

### Frontend
- None in this repo — see the paired baha-ui branch `059-feat-social-auto-post` for the admin
  incident-modal toggle, caption composer, and AdminPanel social-post list.

### Backend
- `config/SocialProperties.java` (new): `@ConfigurationProperties("baha.social")` — master
  `enabled` switch (default false), `dryRun` (default **true** — logs the payload instead of
  calling the network so a misconfigured deploy can't post garbage), `maxAttempts`, this backend's
  own public origin (`apiBaseUrl`, needed because publishing runs detached from any request), and
  nested Facebook Page / X credential blocks.
- `entity/SocialPost.java`, `enumeration/SocialNetwork.java` (`facebook`, `x`),
  `repository/SocialPostRepository.java` (`findByReportId`, `findTop50ByOrderByCreatedAtDesc`,
  `findRetryable`).
- `dto/CreateReportRequest.java`: added optional `socialNetworks` (`["facebook","x"]`) and
  `socialCaption` (client-composed body, max 1000 chars, no URL — the backend appends the deep
  link once the report id exists).
- `service/FloodReportService.create()`: for a `system` report with `socialNetworks` requested,
  inserts `pending` `social_post` rows in the same transaction as the report (a rollback drops
  them too), then registers a `TransactionSynchronization.afterCommit()` hook that fires the
  actual publish. The publish itself is `@Async` (new `ThreadPoolTaskExecutor` bean +
  `@EnableAsync`/`@EnableScheduling` on `BahaApiApplication`) — POST /reports latency is
  unaffected and a Meta/X outage can never fail an admin's report.
- `service/SocialPublishService.java`: composes the final caption (client caption, or a minimal
  fallback built from severity/depth/trend/district, plus the deep link), skips a network when
  disabled globally, disabled via the new `app_setting` flags, or missing credentials (row →
  `skipped` with a reason), delegates to the posters, and records `sent`/`failed` with
  `external_id`/`external_url`/`last_error`. A `@Scheduled(fixedDelayString="PT5M")` sweep retries
  `pending`/`failed` rows under `max-attempts` with a rough exponential backoff.
  In dry-run the row is marked `skipped` (never `sent`) with the reason recorded — nothing reached
  the network, and the admin's Retry can republish it for real once tokens are live.
- `service/social/FacebookPoster.java`: `POST /{page-id}/photos` when the report has a photo
  (URL = this backend's own public photo endpoint, the same one `ShareHtmlService` hands
  crawlers), else `POST /{page-id}/feed` with a `link` back to the report's own
  `GET /reports/{id}/share` OG page so the link-card carries the report's own details.
- `service/social/XPoster.java` + `service/social/OAuth1Signer.java`: posts via X API v2
  (`POST /2/tweets`), hand-signed OAuth 1.0a HMAC-SHA1 (`javax.crypto.Mac`, no new dependency) —
  X user access tokens don't expire, so there's no refresh flow. Uploads a photo first via the
  v1.1 media endpoint when present; truncates to X's 280-char budget (a trailing URL always costs
  23 chars regardless of length) before sending.
- Both posters: 5s connect / 10s read timeouts; a non-2xx is retried by the sweep except
  400/401/403, which mark the row `failed` immediately (a bad token should page a human, not
  retry three times).
- `service/AppSettingService.java` / `dto/SettingsResponse.java` / `dto/UpdateSettingsRequest.java`:
  two new keys, `social_autopost_facebook` and `social_autopost_x`, following the exact
  `chat_enabled` upsert/read pattern — a no-deploy per-network kill switch for the Admin panel.
- `controller/SocialPostController.java` (new): `GET /social-posts` (latest 50, newest first) and
  `POST /social-posts/{id}/retry` (resets attempts, republishes one row), both ADMIN-only.
- `config/SecurityConfig.java`: registered `/social-posts/**` (GET + POST) as ADMIN-only, next to
  the existing `/settings` rules.

### Database
- setup.sql appended: yes — `social_post` table (unique on `(report_id, network)` so re-publishing
  an already-posted report is a DB-level no-op), its indexes/trigger, and the two
  `social_autopost_*` `app_setting` seed rows.
- Migration: `db/migrations/feat/social-auto-post/20260806_social_auto_post.up.sql`
- Rollback:  `db/migrations/feat/social-auto-post/20260806_social_auto_post.down.sql`
  (irreversible: drops the full history of Facebook/X publish attempts)
- **Not yet applied to any database** — run the migration when this branch is deployed.

### Config / Env
- `SOCIAL_ENABLED` (default `false`) — master switch.
- `SOCIAL_DRY_RUN` (default `true`) — log the payload instead of calling the network.
- `SOCIAL_MAX_ATTEMPTS` (default `3`).
- `SOCIAL_API_BASE_URL` (default empty) — this backend's own public origin; required for Facebook
  posting (photo URL / share-page link) to actually run — Facebook posting is skipped, not
  crashed, while it's blank.
- `FB_PAGE_ID`, `FB_PAGE_TOKEN` (default empty), `FB_GRAPH_VERSION` (default `v21.0`).
- `X_API_KEY`, `X_API_SECRET`, `X_ACCESS_TOKEN`, `X_ACCESS_SECRET` (default empty).
- No secrets committed — every credential env var defaults to an empty string.

### Testing
- `mvn -q -DskipTests compile` and `mvn -q test` — both clean.
- Not yet exercised against real Facebook/X — see external prerequisites below; run with
  `SOCIAL_DRY_RUN=true` until they clear.
- **External prerequisites (gate the "live" date):** Meta app with `pages_manage_posts` +
  `pages_read_engagement`, App Review (required to post to a Page you don't own as a developer),
  and a long-lived Page token (or non-expiring Business Manager System User token). X: the Free
  tier's write cap (~500 posts/month) is too low for production use — Basic tier, an Elevated
  project with read-write app permissions, and access token/secret regenerated *after* switching
  to read-write.

---

## [058-feat-admin-report-flood-via-incident-modal] — 2026-08-06

**Summary:** Admins had two report entry points in the top bar — the shared blue "Report flood"
button (which opened the Detailed/Quick chooser and dropped them into the citizen pin +
`SeverityPicker` flow) and a separate indigo 🗞 "Report incident" button for the richer
`AdminReportIncidentModal` (intersection lookup, map pin fine-tune, source, "reported N hours ago",
TTL, flood-prone tagging). Admins log floods they read about, not floods they're standing in, so the
citizen flow was dead weight. "Report flood" now opens the incident modal directly for admins and the
separate incident button is gone. Two defaults were also tightened to match real usage: Source is now
a dropdown preselected to **News**, and Low flooding seeds **8 cm** instead of 18 cm.

Citizens are unaffected — the Detailed/Quick popover and pin flow are unchanged for them.

### Frontend
- baha-ui v1.52.0:
  - `src/components/panels/TopBar.tsx`: removed the standalone `incidentBtn` (desktop) and its mobile
    overflow `MenuItem`. The primary "Report flood" button now calls `onReportIncident()` when
    `isAdmin`; the `ReportOptions` popover and its `aria-expanded`/`aria-haspopup` are gated on
    `!isAdmin`. The `onReportIncident` prop is kept — it is now driven by the primary button.
  - `src/App.tsx`: same branch for the mobile FAB — admins open the incident modal directly; the
    fan-out card, scrim, `+` rotation and `aria-expanded` are gated on `!isAdmin`, and the admin
    aria-label falls back to `topbar.report`.
  - `src/components/panels/AdminReportIncidentModal.tsx`: the free-text "Source / note" input became
    a **Source** `<select>` (`SOURCE_OPTIONS` = News / Social media / LGU advisory / Field report /
    Other) defaulting to `News`, with a conditional free-text box when "Other" is picked. The derived
    `sourceNote` keeps both prior behaviors: appended to the report name (`Aurora cor. Katipunan
    (News)`) and used as the flood-prone zone's `note`. Validation (`canPost`) unchanged.
  - `src/utils/floodMeta.ts`: `SEVERITY_PRESET.low.depthCm` 18 → 8. Global by design — the admin
    modal, `SeverityPicker` one-tap post and `QuickReportSheet` all read the preset. `DEPTH_RANGE.low`
    is 5–30 so 8 is a valid slider position, and `roadFromDepth(8)` is `passable`, consistent with the
    preset's road. Side effect: `depthReference(8)` reads as the `soles` band instead of `ankle`.
  - `src/i18n.ts`: removed the now-unused `topbar.incident` key.

### Backend
- None.

### Database
- setup.sql appended: no
- Migration: none
- Rollback:  none

### Config / Env
- None.

### Testing
- `npm run typecheck` clean; `npm run build` succeeds and `dist/` is rebuilt and committed.
- Manual (admin session): no 🗞 button in the bar or the mobile ⋯ menu; desktop "Report flood" opens
  the incident modal with no chooser popover; mobile FAB does the same with no fan-out; Source shows
  **News** preselected and reveals a text box on "Other"; Low seeds the depth slider at 8 cm; a
  default post's name ends in ` (News)` and the flood-prone zone note is `News`.
- Manual (citizen/guest session): "Report flood" still opens the Detailed/Quick popover into the pin
  flow; a one-tap **Low** report now posts 8 cm.

## [057-feat-share-message-copy] — 2026-08-06

**Summary:** Reworded the tail of the per-report share message. It used the site-wide `PITCH`
("Track live flood conditions on FloodedArea — live crowd-sourced flood map for the Philippines:
real-time reports, evac centers, road conditions & SOS rescue."), which is long enough to crowd X's
280-char budget, only invites the reader to *track*, and names the brand without an address. The new
tail invites the reader to report too, names the domain, and ends on a colon that leads into the link.

New output (all networks — X, Facebook, WhatsApp, Telegram, email, Copy message):

```
⚠️ MEDIUM flooding reported on Ortigas Ave (News) (Pasig) — ~30 cm, receding. 4 active flood reports nearby.

Track or report live flood conditions on FloodedArea.com — live crowd-sourced flood map:
<link>
```

### Frontend
- baha-ui v1.51.0:
  - `src/utils/share.ts`: added `REPORT_PITCH`; `buildReportShareText` now closes with it instead of
    `` `Track live flood conditions on ${PITCH}` ``. The `⚠️`, severity/place/depth/trend clauses and
    `activityClause` are unchanged, as are `title`, `text` (body + URL + the Facebook follow line) and
    the `ShareText` shape.
  - `PITCH`, `buildShareText` (the "I just reported…" and generic-visitor messages) and
    `buildShareLinks` are untouched — only the per-report message changed.

### Backend
- None.

### Database
- setup.sql appended: no
- Migration: none
- Rollback:  none

### Config / Env
- None.

### Testing
- `npm run typecheck` — clean; `npm run build` — succeeds, `dist/` rebuilt and committed.
- Rendered `buildReportShareText` + `buildShareLinks` through vite-node: body matches the target text
  verbatim, X payload is 222 chars including a 23-char t.co link (under 280), and the degenerate case
  (no district, `trend: 'steady'`, no other active reports) collapses to
  `⚠️ MEDIUM flooding reported on Ortigas Ave (News) — ~30 cm.`

---

## [056-feat-report-card-actions] — 2026-08-06

**Summary:** Cleaned up the action stack on the flood-report detail card. The share button was
labeled "Share to Facebook" in Facebook blue, but the action isn't Facebook-specific — on mobile with
a photo it opens the native share sheet, where the user picks any app; only the desktop fallback goes
to Facebook's web sharer. It is now a neutral, primary-colored **"Share Report"** button. The owner-
only **"📍 Show my location"** button was removed: it recentered the map on *the report*, not on the
user, and duplicated the floating locate control and tapping the marker itself.

### Frontend
- baha-ui v1.50.0:
  - `src/components/panels/ReportDetailCard.tsx`:
    - Share button relabeled `Share to Facebook` → `Share Report`; background `#1877F2` →
      `var(--bw-primary)`; the `ⓕ` glyph replaced by a neutral 24×24 inline-SVG share glyph
      (`SHARE_GLYPH`, `fill="currentColor"`). Click behavior unchanged — still `onShareReport`.
    - Removed the "Show my location" button and the `onShowLocation` prop; the `canRemove` branch
      collapsed back from a fragment to the `confirmRemove` ternary.
  - `src/App.tsx`: dropped the now-unused `onShowLocation` prop at the `ReportDetailCard` call site.
    `setMapFocus` is untouched (≈15 other call sites: locate button, sidebar rows, activity feed,
    dedupe toast).
  - The confirm button ("I'm seeing this too — confirm", `report.confirmCta`) is unchanged, including
    its `canConfirm={!selectedReport.mine}` gating.

### Backend
- None.

### Database
- setup.sql appended: no
- Migration: none
- Rollback:  none

### Config / Env
- None.

### Testing
- `npm run typecheck` — clean (also proves no stale `onShowLocation` reference).
- `npm run build` — succeeds; `dist/` rebuilt and committed.
- Manual: own report → `Share Report` + `Remove my report` only; someone else's report →
  `Share Report` + confirm CTA; share flow opens as before on desktop and mobile layouts.

---

## [055-feat-admin-pin-location-and-zone-tagging] — 2026-08-06

**Summary:** Three fixes to the "where is this flood?" half of the admin workflow. (1) The
intersection finder in *Report a flood incident* was reported as not working. Two independent root
causes, both verified against the live Overpass API. **(a)** OSM stores expanded street names
("Aurora Boulevard"), but the modal's own placeholders say *"e.g. Aurora Blvd"* — a substring search
for `Aurora Blvd` matched **zero** ways in the Quezon City bbox while `Aurora` matched
"Aurora Boulevard", so correctly spelled input reported "no crossing found". **(b)**
`overpass-api.de` failed **5 of 6** probe attempts (`504 Dispatcher_Client…timeout` "server too
busy", once `429 rate_limited`; successful runs took 7–11 s), and `runQuery` collapsed network
errors, 429s, 504s, malformed bodies and genuine no-matches all into `[]`, which the UI rendered as
"No crossing found — check the spelling" — blaming the admin for the upstream's outage. (2) Admin
place fields could only be searched, never pinned, so Name/District were typed by hand. (3) Logging
an incident at a chronically flooded spot and marking that spot flood-prone were two disconnected
trips through the Admin console.

### Frontend
- baha-ui v1.49.0:
  - `src/api/overpass.ts`: rewritten transport + matching; the verified-correct QL escaping helpers
    are unchanged.
    - `streetPattern` now expands street-type abbreviations into alternation groups via a new
      `STREET_TYPE_ALIASES` table (`ave|avenue`, `blvd|boulevard`, `st|street`, `rd|road`,
      `hwy|highway`, `dr|drive`, `ext|extension`, `hts|heights`, `pkwy|parkway`) and rejoins tokens
      with `TOKEN_SEPARATOR` (`[^A-Za-z0-9]+`) so "St Mary" also matches "St. Mary" and "C 5"
      matches "C-5". This is root cause (a).
    - New `IntersectionOutcome` discriminated union (`ok` / `none` / `unknown-city` / `busy` /
      `aborted`) replaces the bare `Intersection[]` contract — only `none` and `unknown-city` may
      ever say "check the spelling". Root cause (b).
    - `runQuery` walks a mirror list (`overpass-api.de`, then `overpass.kumi.systems`, then
      `overpass.private.coffee`), retrying each endpoint twice with 1.5 s/4 s backoff on a `busy`
      outcome. `busy` covers 429, 5xx, network error, our own per-request timeout, malformed JSON,
      and Overpass's "HTTP 200 with an error `remark`" case. `aborted` short-circuits so a closed
      modal never burns the retry budget. Both mirrors were unreachable from the verifying network,
      so they are best-effort extra chances — the retry/backoff is the actual fix.
    - `withTimeout` is hand-rolled on a plain `AbortController` rather than
      `AbortSignal.any`/`AbortSignal.timeout` (Chrome 116+ / Safari 17.4+ / Firefox 124+ only) —
      otherwise every search on an older browser would have failed as `busy`.
    - City scoping now resolves through Nominatim (`geocodeCityBBox`) into a bounding box instead of
      Overpass's exact `area[name="…"]` match, so "QC" and trailing-"City" variants work; an
      unresolvable city returns `unknown-city` rather than a misleading nationwide guess.
      `cityQuery` was removed (superseded by the now-parameterised `bboxQuery`, whose `out center`
      limit is no longer hardcoded to 3).
  - `src/api/geocode.ts`: new `geocodeCityBBox(city, signal)` — `countrycodes=ph`, reorders
    Nominatim's `boundingbox` (south/north/west/east) into Overpass's `s,w,n,e`; never throws.
  - `src/components/panels/AdminReportIncidentModal.tsx`:
    - Outcome-aware messaging: `busy` renders an amber panel with **Retry** and a one-click
      **Pick on map** escape; `unknown-city` names the city that failed to resolve; `none` keeps the
      original copy. A "Still searching — OpenStreetMap can be slow" line appears ~5 s in so the
      7–11 s happy path doesn't look frozen.
    - Bug fix: `onPickPlace` overwrote **District** unconditionally on every pick, so fine-tuning
      the pin after choosing an intersection silently rewrote an admin's typed value. Now gated the
      same way the name write is.
    - New "⚠ Also mark this place as flood-prone" checkbox + radius input (default 500 m, floor
      50 m) under Severity, with a local `SEV_TO_LEVEL` map (high→red, medium→orange, low→yellow).
      `onSubmit` widened to `(b: NewReport, zone?: NewFloodProneArea) => Promise<void>`.
  - `src/components/panels/AdminPanel.tsx`: the shared `type: 'place'` field (Shelters, Zones,
    Reports) gains a `MapPickField` mini-map under `PlaceField`; both route through one
    `pickLocation()` that reverse-geocodes and fills Name/District **only when blank**, plus an
    explicit "Resolve name from pin" button that force-overwrites. In-flight reverse-geocodes are
    aborted on a newer pick/unmount. Place fields are wrapped in a `div` instead of a `label` — a
    click inside a `<label>` activates its first form control, which would have yanked focus to the
    search box on every map click and fought the marker drag.
  - `src/App.tsx`: `onReportIncident(b, zone?)` creates the report first (unchanged throwing
    contract so the modal stays open on failure), then best-effort creates the zone in its own
    try/catch — a zone failure only changes the toast text, never the report.
  - `package.json`: 1.48.0 → 1.49.0. `dist/` rebuilt.

### Backend
- None. `POST /flood-prone` (`FloodProneAreaController`) and `mutations.createFloodProneArea`
  already existed; this branch only calls them from a new place.

### Database
- setup.sql appended: no
- Migration: none — no schema change
- Rollback:  none

### Config / Env
- None. Overpass and Nominatim remain keyless, CORS-open, direct-from-browser calls.

### Testing
- `npm run typecheck` clean; `npm run build` clean.
- Verified against the live Overpass API before and after: `Aurora Blvd` matched 0 ways;
  `Aurora` matched "Aurora Boulevard"; the alias-expanded query
  `name~"Aurora[^A-Za-z0-9]+(blvd|boulevard)"` × `Katipunan[^A-Za-z0-9]+(ave|avenue)` returned the
  real crossing nodes at 14.6317615, 121.0740667.
- Browser smoke test (`npm run dev:mock`, seeded ADMIN session):
  - `Quezonn Cityy` → *"Couldn't find "Quezonn Cityy" — check the spelling, or leave City blank."*
    (resolves fast, no Overpass call) — distinct from the no-match copy.
  - `Aurora Blvd` / `Katipunan Ave` / `QC` → **two crossings returned**; the same input returned
    nothing before this branch. Selecting one recentred the mini-map on the real
    Aurora Boulevard × Katipunan Avenue junction and auto-filled Name/District ("3rd District").
  - Posting with "Also mark this place as flood-prone" + radius 400 toasted *"System report posted.
    Flood-prone zone added."* and created a zone with `radiusM: 400`, `level: red`,
    `lastWarnedAt: null` — quiet, no broadcast, as designed.
  - Admin → Zones → Add zone: search auto-filled Name "Cambridge Street" / District "District I";
    a typed name survived a subsequent map click (no clobber); "Resolve name from pin" overwrote it
    to "Harvard Street" on demand.
  - Not covered: the `busy` retry/mirror path was not reproduced in-browser (Overpass happened to
    be healthy during the run); it is exercised only by the direct API probes above.

---

## [054-feat-flood-prone-zones] — 2026-08-05

**Summary:** An admin can now pin persistent **flood-prone areas** (a point plus a radius) that
every visitor sees drawn on the map, and can *separately* trigger a manual warning for one of those
zones. The two actions are deliberately distinct: a zone sits quietly as a standing advisory and can
be re-warned every time conditions justify it. Neither existing surface expressed this — a flood
report asserts water is there *right now*, is crowd-confirmable and expires, while `flood_alert` had
no geometry whatsoever, so every alert was global and could be neither drawn on the map nor targeted
at the people actually near it.

The central design decision was **not to build a second notification pipeline**. The warning rides
the existing alert rails (`GET /alerts` → `useAlerts` → `AlertRibbon` → `AlertModal` →
`alert-created` SSE); `flood_alert` simply gained nullable geometry. Three consequences: the SSE
layer needed *zero* changes (`useActivityStream` already listens for `alert-created`, and App
already refetches on any `alert*` event); proximity gating collapsed to one pure predicate over the
alert list instead of new infrastructure; and the change is fully backward compatible — an alert
with `latitude IS NULL` is global and behaves exactly as before.

### Frontend
- baha-ui v1.48.0:
  - `src/utils/viewModel.ts`: new pure `alertsForUser(alerts, userLoc)` — the entire proximity gate.
    Alerts without geometry always pass; geo-stamped alerts pass within `radiusM/1000 + 0.3 km`
    (`PROXIMITY_MARGIN_KM`), reusing `kmBetween` from `utils/geo.ts`. **Fails open** when `userLoc`
    is null: a missed flood warning is worse than a spurious one, and that reasoning is recorded in
    the doc comment so it isn't "tidied up" later.
  - `src/App.tsx`: `useFloodProneAreas()` + `floodProneList`; `visibleAlerts = alertsForUser(...)`
    now feeds **both** the ribbon (`activeAlert`) and the severe-alert modal effect — gating one but
    not the other would have leaked warnings to people far away. `setAlertModal` tags
    `source: 'zone'` when `areaId != null`. Zone CRUD goes through the existing `adminAction`
    helper; `warnZone` deliberately does **not**, since it must throw so the modal keeps its form
    open on failure (same reasoning as `onReportIncident`) and must refetch both alerts and zones
    (for `lastWarnedAt`). New `zoneWarnTarget` state follows the `{x && isAdmin && <Modal/>}`
    invariant. The four synthetic `FloodAlert` literals (trip/nearby dialogs) gained explicit null
    geometry.
  - `src/components/panels/AdminWarnZoneModal.tsx` (new): the manual trigger. Prefills title/body/
    level from the zone and requires an explicit "Send warning" click — it must never fire as a side
    effect of saving a zone.
  - `src/components/panels/AdminPanel.tsx`: new `zones` tab reusing the existing `'place'` field
    primitive for the map pin, plus a per-row `⚠ Warn` button and a "last warned" hint.
  - `src/components/map/FloodMap.tsx`: dashed `Circle` per zone (level-colored via `LEVEL_STYLE`),
    drawn *below* the report layers so live reports stay on top.
  - `src/components/panels/AlertModal.tsx`: `'zone'` added to `AlertSource`/`SOURCE_KEY`, so a zone
    warning is distinguishable from Nearby / Trip / Weather at the moment it interrupts you.
  - `src/i18n.ts`: `zone.*` group + `alert.sourceZone` (en/fil).
  - `mock-api/`: three seeded zones, explicit null geometry on the existing alert, and explicit
    `/flood-prone` routes — the collection is `floodProneAreas` but the path is `/flood-prone`, so
    json-server's name-derived auto-CRUD does not cover it.
  - `src/hooks/useActivityStream.ts`: **intentionally untouched.**

### Backend
- `entity/FloodProneArea.java`, `repository/`, `service/`, `controller/`, `dto/WarnAreaRequest.java`
  — modeled file-for-file on the `FloodAlert` stack.
- `FloodProneAreaService.warn(id, req)`: builds a geo-stamped `FloodAlert` (title/body/region/level
  falling back to the zone's own fields), saves it, stamps `lastWarnedAt`, and calls
  `sse.broadcast("alert-created", …)` — reusing the existing event name rather than inventing one.
- `entity/FloodAlert.java`: added `latitude`, `longitude` (BigDecimal), `radiusM`, `areaId`.
  `FloodAlertService.patch` was left alone — geometry is only ever set by the warn path.
- `config/SecurityConfig.java`: `GET /flood-prone` public; writes ADMIN-only. `GET /flood-prone/all`
  is matched **before** the public GET line, or it would fall through to `permitAll()`.
- Endpoints: `GET /flood-prone`, `GET /flood-prone/all`, `POST /flood-prone`,
  `PATCH /flood-prone/{id}`, `DELETE /flood-prone/{id}`, `POST /flood-prone/{id}/warn`.

### Database
- setup.sql appended: yes (new `flood_prone_area` table + the four `flood_alert` columns)
- Migration: db/migrations/feat/flood-prone-zones/20260805_flood_prone_zones.up.sql
- Rollback:  db/migrations/feat/flood-prone-zones/20260805_flood_prone_zones.down.sql
- New table `flood_prone_area` (radius CHECK 50–20000 m, `idx_flood_prone_area_active`,
  `updated_at` trigger). `flood_alert` gains nullable `latitude`, `longitude`, `radius_m`, and
  `area_id` (FK → `flood_prone_area`, `ON DELETE SET NULL`) plus `idx_flood_alert_area_id`.
- ⚠️ **Must be applied as a superuser.** `flood_alert` is owned by `postgres`, so the `ALTER TABLE`
  statements fail with `must be owner of table flood_alert` when run as `baha_user` — and psql keeps
  going, so a truncated log looks like success while the geometry columns silently never exist.
  Run `psql -U postgres -d baha -f …up.sql` and verify the four columns afterwards.

### Config / Env
- None. No new env vars, no feature flag; the feature is inert until an admin pins a zone.

### Testing
- `npm run typecheck` clean; `npm run build` clean (dist/ rebuilt and committed); backend
  `mvn -DskipTests compile` → BUILD SUCCESS.
- Against the mock: `POST /flood-prone/100001/warn` returned a 201 alert carrying the zone's
  `latitude/longitude/radiusM/areaId`, bumped that zone's `lastWarnedAt` (and no other zone's), and
  emitted `event: alert-created` on `/sse/activity`.
- Proximity gate verified on the three paths that matter: the pre-existing global alert shows
  regardless of location (backward-compat regression check); the geo alert shows at 0.05 km from the
  España Blvd zone and hides at 15.06 km (limit 0.70 km); and with location unknown it shows
  (fail-open).
- Migration idempotency confirmed by re-running the up script.

---

## [053-feat-engagement-polish] — 2026-08-05

**Summary:** Five engagement gaps, four of which were capability the app already had but never
surfaced. The headline is a real defect: the Confirm button on a flood report was gated on `!!auth`,
but its handler `confirmReport()` already opens with `ensureGuest()` — it was designed to silently
mint a guest session for exactly that low-stakes write. The UI gate never let it run, so Confirm
stayed invisible until the visitor had already posted a report by some other path — the inverse of
the intended flow, and the single biggest drag on corroboration counts. Alongside it: the SSE
activity stream drove background refetches but rendered nothing, so the app never looked live;
evacuation centers offered a phone number but no way to actually get there; the three separate alert
systems (personal-radius bell, trip-stop watch, PAGASA rainfall) were all labelled some flavour of
"alerts" and shared one indistinguishable modal; and a guest was nudged to sign in exactly once, via
a toast after one of the two report paths. Frontend-only — no backend, no DB.

### Frontend
- baha-ui v1.47.0:
  - `src/App.tsx`: **the fix** — `canConfirm={!!auth && !selectedReport.mine}` →
    `canConfirm={!selectedReport.mine}`, with a comment warning against restoring the auth gate
    (`mine` is already `false` with no session, and `confirmReport` mints the guest on click).
    New `lastEventAt` state set inside the existing `useActivityStream` callback — the only thing it
    drives is the ticker's pulsing-vs-static LIVE dot; the stream contract (event name only, no
    payload) is deliberately untouched. New `guestNudgeDismissed` state persisted to
    `localStorage['baha_guest_nudge_v1']`. All three `setAlertModal` call sites now tag
    `source: 'nearby' | 'trip' | 'weather'`. Hardcoded nearby-alert titles moved to i18n. The bare
    `onSignIn={() => setLogin({ open: true })}` now carries a reason, so the LoginView reason box
    (which existed but never rendered from the most-used entry point) actually appears.
  - `src/components/panels/ActivityTicker.tsx` (new): slim full-width strip under the TopBar,
    mounted as a sibling of `AlertRibbon` (same `flex:'none'` / `zIndex:35` pattern). Renders
    `feed[0]` — severity dot from `SEV_META`, district, street, depth, `timeAgo` — so the SSE-driven
    refresh is visibly live. Dismissal is keyed to the report id, so a newer report brings it back.
    Ellipsises to one line on mobile; renders `null` when there are no active reports.
  - `src/utils/geo.ts`: new pure `directionsUrl(lat, lng)` — Google Maps deep link
    (`/maps/dir/?api=1&destination=…&travelmode=driving`), hands off to the native app on iOS/Android.
    Side-effect-free per the `utils/` convention.
  - `src/components/panels/EvacDetailCard.tsx`: the lone "Call" button became a two-button row —
    **Directions** (`var(--bw-primary)`, `target="_blank" rel="noopener noreferrer"`) and **Call**
    (unchanged `tel:` behavior). Deliberately labelled "Directions", never "Route", so it does not
    read as the existing trip feature (which is a flood watchlist, not navigation). Card was
    previously 100% hardcoded English; now uses `useI18n` throughout.
  - `src/components/panels/AlertModal.tsx`: new optional `source` prop rendering a small chip
    (Nearby / Trip / Weather). This modal is shared by all three alert systems — App synthesizes a
    `FloodAlert` with a negative id for report-based alerts — so until now a trip alert, a nearby
    alert and a real PAGASA warning were visually identical at the moment they interrupted you.
  - `src/components/panels/Sidebar.tsx`: dismissible guest card above the alerts block, gated on
    `auth === null || auth.role === 'GUEST'`. Receives the *real* `auth`, not the guest-hidden
    version TopBar gets, since it must tell a guest apart from no session at all.
  - `src/views/LoginView.tsx`: labelled "Keep browsing as guest" button — previously the only way
    back to guest browsing was an unlabelled `×` or a backdrop click.
  - `src/components/panels/TopBar.tsx`: hardcoded `title`/`aria-label="Flood alerts"` → the new
    `topbar.nearbyAlerts` key (every sibling button already used `t()`).
  - `src/components/panels/FeaturesModal.tsx`: 🔔 "Rainfall alerts" → 🌧️ "Weather warnings" (the 🔔
    collided with the actual bell, which controls a different system). Also corrected a long-standing
    false claim that shelters list their "capacity" — `EvacuationCenter` has no such field.
  - `src/components/panels/ReportDetailCard.tsx`: confirm CTA strings moved to i18n.
  - `src/i18n.ts`: alert relabel — `sidebar.floodAlerts` "Flood alerts" → **"Nearby flood alerts"**
    (retitles the sidebar row and the NotifyModal header at once), `sidebar.alertsOn/Off` and
    `notify.master` reworded to say "near you". `trip.independentNote` names the system in prose and
    was updated in lockstep (en + fil). New keys: `topbar.nearbyAlerts`, `notify.alertTitleOne/Many`,
    `alert.sourceNearby/Trip/Weather`, `report.confirmCta/confirming`, `ticker.*`, `guest.*`, `evac.*`
    — all with both `en` and `fil`.
  - `package.json`: 1.46.0 → 1.47.0. `dist/` rebuilt.

### Backend
- none

### Database
- setup.sql appended: no
- Migration: none
- Rollback:  none

### Config / Env
- none

### Testing
- `npm run typecheck` and `npm run build` both clean in baha-ui.
- Not run in this batch (left for manual verification): the full interactive checklist — most
  importantly the incognito/no-session check that the Confirm button now renders and that clicking it
  mints a guest and increments the count; the ticker against a live baha-api (the json-server mock's
  SSE stub emits nothing but `rescue-created`, so the pulsing LIVE dot cannot be exercised offline);
  the relabels walked in both `en` and `fil`; and the guest nudge's dismiss-persistence across reload.

### Follow-up for baha-api
- `FloodReport` has `reportsCount` but no `confirmedByMe` flag (compare `Feedback.upvotedByMe`, which
  does have one). The UI therefore cannot grey out or suppress a repeat confirm — only the server
  dedupes, and the user gets no feedback that they already confirmed. Adding `confirmedByMe` to the
  `/reports` payload would let the frontend reflect that state.
- The `/sse/activity` stream sends event names with no body, so no consumer can know *what* changed.
  053 works around this by re-deriving the newest report from the refetched list. If the stream ever
  carries a JSON payload, the ticker could show the exact event that fired.

---

## [052-feat-trip-stop-monitoring] — not recorded

**Note:** baha-ui v1.46.0 (`052-feat-trip-stop-monitoring`, merged 2026-08-05) shipped without a
CHANGELOG entry here. Recorded as a known gap rather than reconstructed after the fact.

---

## [051-feat-report-entry-simplify] — 2026-08-04

**Summary:** 050 shipped a Detailed/🚗 Quick toggle *inside* the report sheets, so the choice of how
to report was only discoverable after the reporter had already started reporting — and the detailed
form's five fields (photo, street, severity, depth slider, name/anonymous) made a routine flood
report take far more taps than it should for someone standing in water. This reworks the entry point:
one floating button/menu that fans/pops out exactly two options — **Report flood** (detailed) and
**Quick report** (voice-first) — remembers which was chosen last (ordering/highlighting it next
time), and collapses the detailed form to a single tap for the common case. The mobile FAB moves to
bottom-center. Reverts part of 050: `ReportModeToggle.tsx` is deleted, since the mode choice now
happens before the sheet opens rather than inside it. Frontend-only — no backend, no DB.

### Frontend
- baha-ui v1.45.0:
  - `src/components/panels/ReportOptions.tsx` (new): the two-option chooser content (📝 Report flood
    / 🎤 Quick report, each with a subtitle), rendered by both the mobile fan-out and the desktop
    popover. Orders/highlights the preferred option first based on the remembered preference; both
    options stay clickable regardless.
  - `src/App.tsx`: mobile FAB moved from `right:16` to bottom-center
    (`left:'50%', transform:'translateX(-50%)'`), and tapping it now fans out `<ReportOptions>`
    directly above it (via the existing `popIn` keyframe) behind a tap-anywhere scrim that only
    closes the fan-out — it never starts a report. New `reportPref` state persisted to
    `localStorage['baha_report_mode_v1']` (`'report' | 'quick'`, default `'report'`), following the
    existing `loadLivePref`/`LIVE_STORAGE_KEY` pattern. `switchReportMode` and the old `startReport`
    are replaced by a single `beginReport(target: 'report' | 'quick')` — the shared preamble
    (guest session, duplicate-report guard, clearing `selectedReportId`/`selEvacId`, remembering the
    preference, dropping the pin) used by both entry points; `startReport`/`startQuickReport` are
    thin wrappers over it. Quick mode is now reachable directly (previously only reachable by
    switching out of an already-open detailed report), so it now goes through the same guest/
    duplicate-report guards the detailed flow always had. The desktop floating Detailed/🚗 Quick
    pill above the map is removed — `TopBar`'s "Report flood" button now opens the same
    `<ReportOptions>` chooser as a popover. Kept unchanged: the `mode === 'idle'` gate on
    `ReportDetailCard`/`EvacDetailCard` and the `cancelReport()` call in the duplicate-report toast's
    View action — the actual guards against two stacked bottom sheets.
  - `src/components/panels/TopBar.tsx`: new `onChooseReport`/`reportPref` props (replacing `onReport`);
    the desktop "Report flood" button now opens a `<ReportOptions>` popover anchored beneath it
    (click-outside-to-close, mirroring the existing mobile overflow-menu pattern) instead of jumping
    straight into the detailed flow.
  - `src/components/panels/SeverityPicker.tsx`: restructured for progressive disclosure. Collapsed
    (default): a location line, "How bad is it here?", and the three severity buttons post
    IMMEDIATELY on tap using `SEVERITY_PRESET[sev].depthCm`/`trend`/`road` and the reverse-geocoded
    street, plus an "Add details" expander and Cancel. Expanded: reveals photo attach, the editable
    street field, the depth slider + `DepthReference`, and the name/anonymous toggle; a severity tap
    now only *selects* (seeds the depth slider) and an explicit "Post flood report" button submits.
    The `onPick(sev, depthCm, identify, road, captchaToken?, photoDataUrl?)` contract to `App.tsx`'s
    `pickSeverity` is unchanged in both states. CAPTCHA behavior is unchanged (`TurnstileWidget`
    still renders when `captchaRequired`, gating both the collapsed one-tap buttons and the expanded
    Post button). Removed the `mode`/`onSwitchMode` props and inline `ReportModeToggle` render.
  - `src/components/panels/QuickReportSheet.tsx`: auto-starts `startVoice()` on mount when
    `speechSupported()`, so voice costs zero extra taps (added prompt copy "Say: high / medium /
    low"). The floating "Voice Report" FAB (from 050) is repurposed as the re-arm/stop control —
    since `listenForSeverity` is single-utterance, a successful *voice-matched* post now auto-re-arms
    listening after a short delay (tracked via a ref so the delayed callback never reads stale
    `busy`/`listening` state); a *tap* post never re-arms, and unmounting clears the re-arm timer
    before it can fire. Removed the `mode`/`onSwitchMode` props and inline `ReportModeToggle` render.
  - `src/components/panels/ReportModeToggle.tsx`: deleted (reverts part of 050 — the mode choice now
    happens before the sheet opens).
  - `src/i18n.ts`: new keys (en + fil) `report.chooseHow`, `report.optionDetailedSub`,
    `report.optionQuick` + `…Sub`, `report.addDetails`, `report.hideDetails`, `quick.voiceSay`.
    Reused `topbar.report` ("Report flood") for the detailed option's label instead of duplicating
    it. Retired `report.modeDetailed` / `report.modeQuick` (only used by the deleted
    `ReportModeToggle`).
  - `package.json`: 1.44.0 → 1.45.0. `dist/` rebuilt.

### Backend
- none

### Database
- setup.sql appended: no
- Migration: none
- Rollback:  none

### Config / Env
- none

### Testing
- `npm run typecheck` and `npm run build` both clean in baha-ui.
- Not run in this batch (left for manual verification): interactive browser testing of the mobile
  fan-out (centering, scrim dismiss, one-tap post, voice auto-listen/re-arm), the desktop popover,
  and the guest/CAPTCHA path per the plan's verification checklist.

---

## [050-feat-quick-report-admin-polish] — 2026-08-04

**Summary:** A batch of six polish fixes surfaced after v1.43.0 shipped the in-transit quick-report
mode. (1) The community chat bubble had no way to be taken down — added a global, admin-controlled
`app_setting` table + `GET/PATCH /settings` endpoint and a toggle in the Admin console. (2) The
tap-to-talk mic was buried inside the quick sheet's button stack — relocated to a floating "Voice
Report" FAB anchored above the sheet. (3) Two concrete mobile-only defects made the Detailed/🚗 Quick
toggle unreachable and let two bottom sheets stack — fixed by extracting the toggle into its own
component (rendered inline on mobile) and clearing the selected report/evac on every mode switch. (4)
Admin incident reporting could only pin a place by free-text search, which can't resolve PH-style
street-intersection reports ("Aurora Blvd cor. Katipunan Ave") — added an Overpass-API intersection
finder plus a map-pick fallback, both feeding a shared mini-map for fine-tuning. (5) Place search
returned worldwide results for a Philippines-only product — added `countrycodes=ph`. (6) `TopBar` hid
Sign out behind `!isMobile`, so a mobile user could never sign out — added it to the mobile overflow
menu.

### Frontend
- baha-ui v1.44.0:
  - `src/types.ts`: new `AppSettings { chatEnabled: boolean }`.
  - `src/api/queries.ts`: `rawApi.settings` (`GET /settings`) and `mutations.updateSettings`
    (`PATCH /settings`).
  - `src/hooks/useDomainData.ts`: new `useSettings()`, same shape as the other domain-data hooks.
  - `src/App.tsx`: mounts `<ChatWidget>` only when `settings.data?.chatEnabled !== false`
    (default-on, so a fetch failure never silently hides chat); new `onToggleChat` admin handler
    (`mutations.updateSettings` → `settings.refetch(true)`, toasts on failure). `switchReportMode`
    now clears `selectedReportId`/`selEvacId` on every mode switch; `ReportDetailCard`/
    `EvacDetailCard` are gated on `mode === 'idle'`; the duplicate-report toast's **View** action
    (in `pickSeverity`) calls `cancelReport()` before selecting the existing report — all three
    close the fix for the two-bottom-sheets-stack defect. The Detailed/🚗 Quick floating pill above
    the map is now desktop-only (`!isMobile &&…`), using the new `ReportModeToggle`.
  - `src/components/panels/ReportModeToggle.tsx` (new): the segmented Detailed/🚗 Quick control,
    extracted out of `App.tsx` so it can be rendered either as App's floating pill (desktop) or as
    the first row inside the sheet card (mobile — see `SeverityPicker`/`QuickReportSheet` below).
  - `src/components/panels/SeverityPicker.tsx`, `QuickReportSheet.tsx`: new `mode`/`onSwitchMode`
    props; render `<ReportModeToggle>` as the sheet's first row when `isMobile`, fixing the
    unreachable-toggle defect (the floating pill sat in the map area's `isolation: isolate`
    stacking context, which the sheets' full-viewport dimmer — same zIndex bracket — painted over).
  - `src/components/panels/QuickReportSheet.tsx`: removed the in-card mic button/row; added a
    floating "Voice Report" FAB (styled after `ChatWidget`'s bubble: gradient pill, pulse when
    listening) anchored via `position:absolute; right:16; top:-64` to a non-scrolling sheet
    wrapper, so it floats clear of the card and dimmer on both mobile and desktop. Reuses the
    existing `speechSupported()`/`startVoice`/`stopVoice`/`listenForSeverity` wiring unchanged — a
    relocation, not a rewrite.
  - `src/i18n.ts`: `quick.voiceTap` shortened to `'Voice Report'` / `'Ulat sa Boses'` (was "Tap to
    report by voice").
  - `src/components/panels/AdminPanel.tsx`: new `chatEnabled`/`onToggleChat` props; a second toggle
    row ("Community chat button") directly under the existing Live-activity-stream row, cloning its
    switch markup.
  - `src/api/geocode.ts`: `searchPlaces` now sends `countrycodes=ph`; updated the module header,
    `searchPlaces` docblock, and `AdminReportIncidentModal` docblock, which previously promised
    worldwide search.
  - `src/api/overpass.ts` (new): `findIntersection(a, b, city, signal)` — POSTs an Overpass QL query
    resolving the shared node(s) of two named streets (optionally scoped to an administrative
    area), falling back to a rough Philippines bbox when the named area yields nothing. Same
    never-throws/`[]`-on-error contract as `geocode.ts`; fires only on an explicit button press.
  - `src/components/panels/MapPickField.tsx` (new): a compact (~220px) react-leaflet map with a
    draggable marker and click-to-move, for fine-tuning a picked point.
  - `src/components/panels/AdminReportIncidentModal.tsx`: replaced the single `PlaceField` with a
    two-tab Location block — **Intersection** (Street 1 / Street 2 / City + "Find intersection" →
    results list, default tab) and **Pick on map** (the existing `PlaceField` search/current-location
    field); both feed a shared `MapPickField` below for fine-tuning, which re-runs `reverseGeocode`
    on every move. `onPickPlace` gained a `skipNameOverwrite` flag so picking (or fine-tuning) an
    intersection result keeps the "A cor. B" name instead of being clobbered by a reverse-geocode
    of the crossing node (which typically resolves to only one of the two streets).
  - `src/components/panels/TopBar.tsx`: mobile overflow menu gained a divider, a non-interactive
    account-name header row, and a Sign out item (`auth &&…`) at the end — previously Sign out was
    only reachable via the desktop-only inline button, so a mobile user could never sign out.
  - `mock-api/server.cjs` + `mock-api/db.json`: `GET`/`PATCH /settings`, backed by a new `settings`
    key in `db.json` (`{ chatEnabled: true }`).
  - `package.json`: 1.43.0 → 1.44.0. `dist/` rebuilt.

### Backend
- baha-api:
  - New `entity/AppSetting.java` (key/value/updatedAt, `@Table("app_setting")`),
    `repository/AppSettingRepository.java` (`JpaRepository<AppSetting, String>`),
    `service/AppSettingService.java` (`get()` defaults missing/unset to `chatEnabled: true`;
    `patch(UpdateSettingsRequest)` upserts only non-null fields), `dto/SettingsResponse.java`
    (`boolean chatEnabled`), `dto/UpdateSettingsRequest.java` (`Boolean chatEnabled`, null =
    unchanged), `controller/SettingsController.java` (`GET /settings`, `PATCH /settings`).
  - `config/SecurityConfig.java`: `GET /settings` public, `PATCH /settings` ADMIN-only.
  - Verified end-to-end (not just migrated): `mvn -q -DskipTests compile` clean; the entity ↔
    repository ↔ service ↔ DTO ↔ controller ↔ SecurityConfig chain for `chatEnabled` was traced by
    hand to confirm it's fully wired (this project has previously shipped migrations/specs without
    the Java actually wired).

### Database
- setup.sql appended: yes — `app_setting` table (`key TEXT PRIMARY KEY, value TEXT NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`), seeded `('chat_enabled','true')` via
  `ON CONFLICT DO NOTHING`, plus the standard grants block.
- Migration: `db/migrations/feat/app-settings/20260804_app_settings.up.sql`
- Rollback:  `db/migrations/feat/app-settings/20260804_app_settings.down.sql` (drops the table —
  irreversible loss of any admin-set settings, noted in the file)

### Config / Env
- none

### Testing
- `npm run typecheck` and `npm run build` both clean in baha-ui.
- `mvn -q -DskipTests compile` clean in baha-api.
- Not run in this batch (no live DB / dev server per the task instructions — left for manual
  verification): applying the migration and hitting `GET /settings` / `PATCH /settings` against a
  running baha-api; exercising the chat toggle, the mobile mode-toggle/no-stacking fix, the Voice
  Report FAB, PH-only search, the intersection finder, and mobile Sign out in the browser.

---

## [049-feat-in-transit-quick-report] — 2026-08-04

**Summary:** Added a "Quick / Driving" reporting mode for users reporting a flood while in transit
(e.g. a passenger in a moving vehicle), alongside the existing detailed report flow. Reporting from a
moving vehicle previously required the same interaction as reporting from home — adjust a map pin,
choose severity, dial a depth slider — too much friction for someone on the move. Quick mode adds a
one-tap severity picker (High/Medium/Low) that posts instantly at the live GPS location, using the
existing `SEVERITY_PRESET` defaults for depth/trend/road, with no pin dragging or depth slider. The
sheet stays open after each post so a passenger can mark several flooded points along a route, with an
Undo action on each post's confirmation toast. Severity selection also supports tap-to-talk voice
input (Web Speech API) as a progressive enhancement — the tap buttons remain the always-available
fallback. No backend/DB changes; reuses the existing `POST /reports` endpoint.

### Frontend
- baha-ui v1.43.0:
  - `src/App.tsx`: `mode` extended from `'idle' | 'report'` to `'idle' | 'report' | 'quick'`. New
    `switchReportMode` to toggle between the detailed and quick forms without leaving report mode; a
    `Detailed / 🚗 Quick` toggle rendered above the report-mode banner. New continuous-GPS effect
    (gated on `mode === 'quick'`) keeps `pending`/`userLoc` glued to the live position via
    `watchLocation`, throttling reverse-geocode calls to actual movement (>120 m) rather than every
    fix. New `quickPost(sev, captchaToken?)` handler — posts immediately at the live location with
    `depthCm`/`trend`/`road` from `SEVERITY_PRESET`, reuses the existing guest/CAPTCHA/duplicate-report
    guards from `pickSeverity`, and (unlike the detailed flow) does not exit report mode on success —
    it shows a toast with an Undo action instead. New `undoQuickReport(id)` deletes a just-created
    quick report via the existing `mutations.deleteReport`.
  - `src/utils/geolocate.ts`: new `watchLocation(onFix)` — a persistent `navigator.geolocation.watchPosition`
    wrapper (vs. the existing `locateWithAccuracy`'s one-shot converge-then-teardown), returning a
    cleanup function.
  - `src/utils/voiceReport.ts` (new): thin Web Speech API wrapper — `speechSupported()`,
    `listenForSeverity()` (single-utterance recognition, keyword-maps the transcript to a `Severity`),
    and `speak()` (TTS confirmation read-back). Self-contained; every entry point no-ops rather than
    throwing when unsupported (Firefox has no SpeechRecognition; iOS Safari is inconsistent).
  - `src/components/panels/QuickReportSheet.tsx` (new): the quick-mode form — safety note, live
    location/accuracy line, three large severity buttons (posts on tap, brief "✓ Reported" flash), an
    optional tap-to-talk mic button (rendered only when `speechSupported()`), optional Turnstile
    widget, and a Done button. Mirrors `SeverityPicker`'s mobile-sheet/desktop-popover layout.
  - `src/i18n.ts`: new `report.modeDetailed` / `report.modeQuick` (toggle labels) and `quick.*` keys
    (safety note, location/status copy, voice prompts, Undo/Undone), in English and Filipino.

### Backend
- none

### Database
- setup.sql appended: no · Migration: none · Rollback: none

### Config / Env
- none

### Testing
- Browser (dev:mock, desktop popover layout): toggled Detailed → Quick from the report banner; tapped
  High/Medium/Low each posted instantly using the `SEVERITY_PRESET` depth (confirmed via the safety
  banner reading "High flooding … about 108 cm" and the road-status counters incrementing
  Impassable/Risky/Passable correctly per severity); the sheet stayed open after each post (multi-point
  marking confirmed); the "✓ Reported" button-flash and the toast-with-Undo both rendered correctly;
  Done correctly exited quick mode back to idle. Mic button rendered (Chrome supports
  `webkitSpeechRecognition`); actual speech recognition not exercised (requires live microphone input,
  outside this sandboxed browser). `npm run typecheck` and `npm run build` both clean. Mock DB seed
  data (`mock-api/db.json`) restored after testing.
- Known test-environment limitation (not a code issue): consecutive tap-severity posts in this sandbox
  can exceed the 250 m same-location duplicate-report radius due to synthetic GPS jitter between
  `watchPosition` fixes — the dedup check itself is the same `nearbyOwnActiveReport` guard already
  proven in the detailed flow.

---

## [048-fix-fb-discoverability-report-singular] — 2026-07-12

**Summary:** Two small polish fixes. (1) The "Follow us on Facebook" link was only reachable after
opening the Share dialog — added a matching row to the About & Support dialog (opened from the TopBar
💙 button) so it's discoverable without ever opening Share. (2) A report confirmed by exactly one
source read "1 reports" (CONFIRMED BY cell) — now singularizes to "1 report", matching the
singular/plural handling already used everywhere else in the app.

### Frontend
- baha-ui v1.42.2:
  - `src/utils/share.ts`: new exported `FB_GLYPH_PATH` (the Facebook brand SVG path), so both
    `ShareModal` and `SupportModal` share one copy instead of duplicating the path string.
  - `src/components/panels/ShareModal.tsx`: reads `FB_GLYPH_PATH` from `share.ts` instead of a local
    copy — no behavior change, purely de-duplication.
  - `src/components/panels/SupportModal.tsx`: new "Follow us on Facebook" row (same pill styling as
    ShareModal's), placed after the "please report only the truth" callout and before "Continue to
    the map"; reuses the existing `share.followFb` i18n key.
  - `src/components/panels/ReportDetailCard.tsx`: "CONFIRMED BY" cell now reads
    `{count} {count === 1 ? 'report' : 'reports'}` instead of always "reports" — matches the
    inline-ternary pattern already used in `RoadConditionsModal.tsx` / `ResponseConsole.tsx`.

### Backend
- none

### Database
- setup.sql appended: no · Migration: none · Rollback: none

### Config / Env
- none

### Testing
- Browser (dev:mock): About & Support modal shows the new Facebook row (opens `FB_PAGE_URL` in a new
  tab); ShareModal's Facebook icon + follow row still render correctly (glyph-extraction regression
  check). A report with `reportsCount === 1` shows "1 report"; one with `reportsCount === 31` still
  shows "31 reports". No console errors. `npm run typecheck` clean.

---

## [047-fix-share-og-canonical] — 2026-07-11

**Summary:** Facebook was showing the report's *image + link but no description* — even though the
backend `/reports/{id}/share` served a correct per-report `og:description`. Two things in that HTML
sent Facebook's crawler away to the generic homepage before it used the report tags: the
`og:url` pointed at the deep-link (Facebook canonicalizes to it and reads the SPA's generic OG), and
a `<meta http-equiv="refresh">` (the crawler follows it through to the SPA). Fixed by making `og:url`
self-referential (points back at the `/share` page) and replacing the meta-refresh with a JavaScript
redirect — crawlers don't run JS, so they keep the report tags; humans still bounce to the deep-link.

Now Facebook/X preview cards show the report's details (severity, depth, trend, location — the
what/where; the app it opens answers *when* via "reported N ago"). Clicking the card opens the report.

### Frontend
- none (baha-ui already routes Facebook/X scraping to `/reports/{id}/share` — see 046).

### Backend
- `ShareHtmlService`: `og:url` is now the `/share` URL itself (was the deep-link); the redirect to the
  app is a `<script>location.replace(...)</script>` (was `<meta http-equiv="refresh">`). Report OG
  content (title/description/image) unchanged. JS-string escaping added for the redirect URL.

### Database
- setup.sql appended: no · Migration: none · Rollback: none

### Config / Env
- none

### Testing
- Backend compiles on JDK 17; rendered HTML validated (jshell): self-referential `og:url`, no
  meta-refresh, `location.replace` present, report details in `og:description`.
- Pending post-deploy: re-scrape a shared report in the Facebook Sharing Debugger (clears FB's cache)
  and confirm the card now shows the report description.

**Known tradeoff:** because the card is built from the API host's `/share` page, Facebook displays
that host as the card's domain (not `floodedarea.com`). Keeping the description **and** the
`floodedarea.com` domain requires the static site to serve per-report OG to crawlers — i.e. the edge
(nginx) crawler-routing option, which prod's static `floodedarea.com` doesn't currently do.

---

## [046-fix-share-og-via-backend] — 2026-07-11

**Summary:** Makes the per-report social share actually work on prod's split-host topology (static
`floodedarea.com` + separate API host). After 045, Facebook still showed the generic site card and
linked to the homepage, because `floodedarea.com` serves only static files — it has no `/api` proxy
and no crawler routing, so it never handed crawlers the per-report Open Graph tags. Now the share
buttons point Facebook/X directly at the backend's `/reports/{id}/share` endpoint (which already
returns the report's details as `og:description` and sets `og:url` to the deep-link), so no
`floodedarea.com`/nginx change is needed. The report's photo `og:image` was also corrected to use the
API's own origin (it 404'd via `floodedarea.com/api`).

Note: the client-side deep-link (`/?report=<id>`) already works on v1.42.0 — earlier "redirects to
main page" reports were a stale PWA service-worker cache serving the pre-deep-link bundle, plus
Facebook linking to the generic `og:url` (fixed here).

### Frontend
- baha-ui v1.42.1:
  - `src/utils/share.ts`: new `buildReportOgUrl(apiBase, id)` → `<apiBase>/reports/<id>/share`;
    `buildShareLinks` takes an optional `ogUrl` (defaults to `url`) and uses it for the Facebook `u`
    and X `url` params — the URL those crawlers scrape to build the preview card. WhatsApp/Telegram/
    email keep the clean human deep-link (details already ride in their message text).
  - `src/App.tsx`: `reportShareData` and the `shareReport` Facebook fallback pass
    `buildReportOgUrl(API_BASE, id)` as `ogUrl`.

### Backend
- `ShareHtmlService.renderReport` now takes the API's public origin (`apiBase`) and builds the photo
  `og:image` from it (`<apiOrigin>/reports/{id}/photo`) instead of `<site>/api/...`, which 404s on the
  static site. Site-cover fallback (no photo) still points at the static site.
- `FloodReportController.share` derives that origin from the request
  (`ServletUriComponentsBuilder.fromCurrentContextPath()`, honoring X-Forwarded-*) — no new config.

### Database
- setup.sql appended: no · Migration: none · Rollback: none

### Config / Env
- none

### Testing
- Frontend (dev:mock, browser DOM): the report share sheet's Facebook `u` and X `url` point at
  `<apiBase>/reports/<id>/share`; WhatsApp/Telegram keep `?report=<id>`. Typecheck + build clean.
- Backend compiles on JDK 17. Prod `/reports/<id>/share` already returns the report details in
  `og:description` and `og:url` = the deep-link (verified via curl).
- Pending post-deploy (needs redeploy of both apps): Facebook Sharing Debugger + X Card Validator on
  a shared report — confirm the card shows the report's details and the click opens the deep-link. If
  Facebook canonicalizes to `og:url` and shows the generic card, the one-line fallback is to make the
  backend `og:url` self-referential (trades the clean domain for guaranteed per-report details).

---

## [045-feat-report-deeplink-share] — 2026-07-11

**Summary:** Sharing a flood report now (1) produces a link that opens the app **directly** on that
report (`/?report=<id>`), and (2) shows the report's own details in the social-media preview card
instead of the generic site cover. Previously every share used the bare app URL and Facebook showed
the site-wide "just the map" preview. The report's "Share to Facebook" button now opens the full
multi-network share sheet (Facebook, X, WhatsApp, Telegram, email, copy link) scoped to that report.

Because social crawlers don't run JavaScript, the per-report preview card is served server-side: a
new public HTML endpoint emits per-report Open Graph tags, and nginx routes crawler user-agents that
request a shared report link to it. Humans always get the SPA, which reads `?report=<id>` on load.

### Frontend
- baha-ui v1.42.0:
  - `src/utils/share.ts`: new pure `buildReportUrl(origin, id)` → `<origin>/?report=<id>`.
  - `src/App.tsx`: per-report share (`reportShareData`, `shareReport`) now uses the deep-link URL; the
    report card's Share button opens the multi-network `ShareModal` (`setShareReportTarget`) instead of
    jumping straight to Facebook; new mount effect reads `?report=<id>` and, once reports load, selects
    the report and flies the map to it (canonical `setTab`/`setSelectedReportId`/`setMapFocus` pattern).
    An expired/removed id shows a toast; runs once.
  - `nginx/default.conf.template`: `map` detecting social-crawler user-agents; `location /` routes a
    crawler-with-`?report=` to an internal `= /__prerender` that proxies to the backend share endpoint;
    everyone else (and crawlers on the bare root) still get the static SPA. Only `set`/`rewrite last`
    run inside `if` (the safe subset). `nginx -t` passes.

### Backend
- `FloodReportController.java`: new public `GET /reports/{id}/share` returning `text/html` with
  per-report OG/Twitter tags (`text/html`, 5-min cache; falls back to a site-wide card for unknown ids).
  Inherits the existing public `GET /reports/**` rule — no security change.
- New `service/ShareHtmlService.java`: renders the OG HTML; absolute URLs built from
  `baha.public-base-url`; `og:image` = the report's photo via the nginx `/api` proxy (else the site
  cover); `og:url`/`<meta refresh>` = the human deep-link; all interpolated text HTML-escaped.

### Database
- setup.sql appended: no
- Migration: none
- Rollback:  none

### Config / Env
- New `baha.public-base-url` (env `BAHA_PUBLIC_BASE_URL`, default `https://floodedarea.com`) in
  `application.properties` and `docker-compose.yml`.

### Testing
- Frontend (dev:mock, browser): `/?report=1` opens the report card + flies the map; the card's Share
  button opens the multi-network sheet whose links carry `…/?report=1` and the report's details; a bad
  id (`999999`) is handled gracefully with no console errors.
- Backend: compiles on JDK 17; the OG HTML render was validated standalone (jshell) — all tags filled,
  `og:url` = deep-link, `og:image` = `/api/reports/{id}/photo`, injection-safe escaping.
- nginx: `nginx -t` on the substituted template passes.
- Pending post-deploy (requires a public URL): Facebook Sharing Debugger + X Card Validator on
  `https://floodedarea.com/?report=<id>` to confirm the live crawler sees the per-report card.

---

## [044-fix-alert-modal-on-navigate] — 2026-07-11

**Summary:** Fixed a defect where the full-screen alarm modal (with sound) re-popped on *every*
page load/navigation for any already-active red/orange rainfall alert — even when there was no
flooding near the user (rainfall alerts are region-wide, not location-scoped). The modal effect had
no priming, and its in-memory "already shown" tracker reset on each load, so an ongoing warning was
treated as brand-new on every visit. Now the modal primes on first settled load like its sibling
effects: an alert already ongoing when the user arrives is surfaced only by the persistent
AlertRibbon banner, and the alarm modal fires only for genuinely new or escalated (e.g. orange → red)
alerts.

### Frontend
- baha-ui v1.41.1 (`src/App.tsx`): severe-alert effect now returns early while `alerts.loading` so it
  primes against real data, not the empty initial render; primes `modaledAlertLevels` (alongside
  `notifiedAlertLevels`) on first settled load without alerting; the modal + alarm and the
  OS-notification/toast now fire only for newly-active/escalated alerts. Added `alerts.loading` to the
  effect deps.

### Backend
- none

### Database
- setup.sql appended: no
- Migration: none
- Rollback:  none

### Config / Env
- none

### Testing
- `npm run typecheck` passes. Verified against the mock backend's seeded active red alert: the alarm
  modal no longer pops on page load; the AlertRibbon banner still shows the ongoing warning.

---

## [043-feat-archive-retain-10] — 2026-07-11

**Summary:** The Archive tab now retains the 10 most recent expired flood reports with no time limit
(was: expired reports dropped off after a rolling 12-hour window). When there is no new activity the
archived reports stay put, and they are only pushed out as newer reports expire. This gives users a
lasting record that flooding happened in the past instead of an empty tab during quiet periods.

### Frontend
- baha-ui v1.41.0 (`src/App.tsx`): replaced the `ARCHIVE_WINDOW_MIN` (12h) time filter with an
  `ARCHIVE_MAX = 10` recency cap. `archived` now filters expired, non-resolved reports, sorts by
  `createdAt` desc, and `.slice(0, 10)`. Dropped the unused `now` dependency from the memo (the
  underlying `enriched` still recomputes each tick, so newly-expired reports enter the archive).

### Backend
- None. `GET /reports` already returns all `status=active` reports ordered by `createdAt` desc with no
  age-based pruning, so expired-by-TTL reports remain available for the client to archive indefinitely.

### Database
- setup.sql appended: no
- Migration: none
- Rollback:  none

### Config / Env
- None.

### Testing
- `npm run typecheck` clean; `npm run build` succeeds; `dist/` rebuilt and committed.

---

## [fix/persistent-login] — 2026-07-10

**Summary:** Keep users signed in until they explicitly Sign out. The UI now persists the session in
`localStorage` (was per-tab `sessionStorage`, which dropped on tab close/reopen/back-navigation);
this backend change lengthens the JWT lifetime so the token doesn't expire during normal use and
force a re-login. The 401/403 auto-logout stays as the revocation path for a genuinely invalid token.

### Frontend
- baha-ui v1.40.1: auth profile + JWT moved from `sessionStorage` → `localStorage` (`useAuth.ts`,
  `client.ts`), with a one-time migration of any legacy per-tab session; Sign out clears both stores.

### Backend
- `jwt.expiration-ms` default raised from `86400000` (24h) to `2592000000` (30 days) in
  `application.properties` and `application-dev.properties`. `JwtUtil` already applies it to the
  token `exp`; no Java change. `JWT_EXPIRATION_MS` env override still wins in prod.

### Database
- setup.sql appended: no · Migration: none · Rollback: none

### Config / Env
- `JWT_EXPIRATION_MS` default is now 30 days (2592000000 ms). Set it lower to shorten sessions.

### Testing
- Booted baha-api; logged in; decoded the issued JWT `exp` → ~30 days out (was ~24h). Authed request
  (`POST /reports`) still succeeds. Frontend: signed in, opened a new tab / reloaded → still signed
  in; Sign out → cleared; legacy `sessionStorage` session migrates forward on first load.

---

## [fix/wire-report-photo-system-ttl] — 2026-07-10

**Summary:** Implement the pending baha-api Java for three features whose DB migrations + frontend
had already shipped but whose backend was never wired: report photos (v1.35.0), admin "system"
reports (v1.36.0), and admin-defined TTL + source report time (v1.37.0). Fixes the reported defect
where **admin-defined TTL was ignored and the client fell back to the severity-scaled default** —
the API silently dropped `ttlMinutes`/`reportedAt` (not in the DTO/entity) and always returned them
absent, so `enrichReports` never saw an override. No new DB migration (columns already applied).

### Frontend
- None (baha-ui v1.35.0/v1.36.0/v1.37.0 already shipped the client side).

### Backend
- `CreateReportRequest`: added optional `photoDataUrl` (`@Size`-bounded), `system` (Boolean),
  `reportedAt` (OffsetDateTime), `ttlMinutes` (Integer).
- `FloodReport` entity: mapped `reported_at` / `ttl_minutes`; `@PrePersist` defaults
  `reportedAt = createdAt`; read-only `@JsonIgnore` `photo_mime` + derived `@Transient getPhotoUrl()`
  (bytes stay off list/detail JSON and hot-path SELECTs).
- `FloodReportRepository`: native `setPhoto` (write) + `findPhotoById` projection (read).
- `FloodReportService.create`: gates `system`/`reportedAt`/`ttlMinutes` to ADMIN callers, tags
  `reporterTrust='system'`, skips the geofence check for system reports, and decodes/validates +
  stores an optional photo; new `photo(id)` read.
- `FloodReportController`: new public `GET /reports/{id}/photo` streaming the image (404 when none).
- No SecurityConfig change — `GET /reports/**` already `permitAll()`.

### Database
- setup.sql appended: no (already present) · Migrations already applied on main:
  db/migrations/feat/report-photo, feat/system-report, feat/report-ttl (up/down each).
- `flood_report`: `reported_at`, `ttl_minutes` (CHECK > 0), `photo` BYTEA, `photo_mime` TEXT
  (MIME + pairing CHECKs); `reporter_trust` CHECK widened to include `'system'`;
  `v_active_flood_report` includes reported_at/ttl_minutes. No new objects this branch.

### Config / Env
- None.

### Testing
- Local Postgres (schema baha, migrations present); app booted (`mvn spring-boot:run`).
- ADMIN `POST /reports {system:true, reportedAt:-5h, ttlMinutes:360, photoDataUrl}` at Cebu
  (outside geofence) → 201, echoes `reporterTrust:"system"`, `ttlMinutes:360`, backdated
  `reportedAt`, `photoUrl`; geofence bypassed.
- `GET /reports/{id}` echoes all four fields; `GET /reports/{id}/photo` → 200 `image/png`,
  cache-control public, valid bytes; list JSON leaks no raw photo bytes (only `photoUrl`).
- Negative: CITIZEN `system+ttl` outside geofence → 422 (enforced, flag ignored); at a valid
  location → 201 with `reporterTrust:"verified"`, `ttlMinutes:null` (admin-gated). Test rows/users
  cleaned up afterward.

---

## [fix/chat-bubble-label] — 2026-06-30

**Summary:** Make the community-chat launcher more discoverable, especially on mobile, where the
icon-only circle was easy to miss.

### Frontend
- `src/components/panels/ChatWidget.tsx`: the collapsed launcher is now a labeled **"💬 Chat"**
  pill (was a 56px icon-only circle) with a stronger shadow. Position unchanged (bottom-right;
  mobile `bottom: 84`, desktop `bottom: 24`).
- UI version bumped `1.33.1 → 1.33.2` (patch). `dist/` rebuilt.

### Backend
- None.

### Database
- setup.sql appended: no · Migration: none · Rollback: none

### Testing
- `npm run typecheck` passes; pill verified in `npm run preview`.

---

## [fix/hide-statcounter-and-donation] — 2026-06-30

**Summary:** Temporarily hide two peripheral UI elements (easy to restore): the visible
Statcounter badge and the "keep the lights on" GCash donation section. Analytics tracking is
preserved.

### Frontend
- `index.html`: Statcounter `sc_invisible = 0 → 1` — hides the visible counter badge while
  `counter.js` still loads and tracks (verified: tracking pings fire with `invisible=1`).
- `src/components/panels/SupportModal.tsx`: the "Support / donate" block (the "Help keep the
  lights on" heading, copy, and collapsible GCash QR) is commented out for now. The `qrSrc`
  prop is left undestructured and the `qrOpen` state removed to satisfy `noUnusedLocals`; both
  noted inline for restoration. The rest of the modal (intro, "report only the truth" callout,
  Continue button) is unchanged.
- UI version bumped `1.33.0 → 1.33.1` (patch). `dist/` rebuilt.

### Backend
- None.

### Database
- setup.sql appended: no · Migration: none · Rollback: none

### Config / Env
- None.

### Testing
- `npm run typecheck` passes; verified in `npm run preview`: donation section gone with the rest
  of the Support modal intact, no visible Statcounter badge, and Statcounter `counter.js` + pings
  still load (`invisible=1`).

---

## [feat/community-chat] — 2026-06-30

**Summary:** Add a free, anonymous community chatroom so flood-watchers can talk in real time
during storms — embedded as a third-party **tlk.io** room, so there is **no backend, no
database, and no new domain types**. It surfaces as a floating chat bubble (bottom-right) that
expands into a panel; the room iframe is lazy-loaded on first open and kept mounted while
collapsed so the conversation persists.

### Frontend
- New `src/components/panels/ChatWidget.tsx` — self-contained floating bubble + expandable
  panel (desktop card / mobile bottom sheet) embedding `https://tlk.io/floodedarea` via a direct
  `<iframe>`. Owns its own open/mounted state (mirrors TopBar's `menuOpen`), so `App.tsx` only
  renders it once. Open to everyone incl. guests; no auth gate; anonymous (tlk.io prompts for a
  per-user nickname — no identity is passed from the app).
  - Note: tlk.io's `embed.js` is **not** used — it auto-scans for its container on
    `DOMContentLoaded`, which has already fired in this SPA, so it never renders. A direct iframe
    is deterministic and tlk.io allows framing (verified — no `X-Frame-Options` block).
- `src/App.tsx`: import + render `<ChatWidget isMobile={isMobile} />` alongside the other
  overlays. No new App state/handlers.
- UI version bumped `1.32.1 → 1.33.0` (new feature). `dist/` rebuilt.

### Backend
- None. tlk.io is a third-party hosted service.

### Database
- setup.sql appended: no
- Migration: none
- Rollback: none

### Config / Env
- None. No CSP is configured, so the iframe works as-is. If a CSP is later added it must allow
  `frame-src https://tlk.io`.

### Testing
- `npm run typecheck` passes. Verified in the production build (`npm run preview`): bubble opens
  the panel, the tlk.io room loads inside the branded header, anonymous join prompt shows, and
  there are no framing/CSP/mixed-content console errors.

### Play Store follow-ups (tracked, not blockers)
- Anonymous UGC ⇒ before TWA submission, add a report/flag path + community guidelines and
  designate a room moderator (Play UGC policy); disclose **tlk.io** as a third-party chat
  processor in the privacy policy + data-safety form (see `baha-ui/PLAYSTORE_PACKAGE.md`).

---

## [feat/feedback-upvotes] — 2026-06-28

**Summary:** Turn feedback into a public, upvotable Community feedback board. Anyone — including
no-login guests — can submit feedback and upvote items; submissions stay `pending` until an admin
accepts them, and accepted items (bugs **and** features) appear on a public board sorted by upvotes.
This also fixes the `403`s users hit after the board shipped: the backend now serves
`GET /feedback/accepted` publicly, and the frontend no longer requests the admin-only `GET /feedback`
for non-admins.

### Frontend
- New `src/components/panels/CommunityFeedbackModal.tsx` — public board (upvote buttons, type chips,
  suggest CTA). Reachable from the TopBar (desktop 💬 button + mobile overflow) and the Features modal.
- `FeedbackFormModal` now opens to guests with a Turnstile CAPTCHA; submission uses the
  `ensureGuest()` + CAPTCHA pattern instead of `requireFullAuth`.
- `Feedback` type gains `upvotes`/`upvotedByMe`; `NewFeedback` gains `captchaToken`. Query
  `acceptedFeatures` → `acceptedFeedback` (both types); new `upvoteFeedback` mutation.
- `useFetch` gains an `enabled` option; `useAllFeedback(enabled)` is gated to admins so non-admins
  don't `403` on `/feedback` at load. AdminPanel shows an upvote count per request.
- UI version bumped `1.31.0 → 1.32.0` (board) → `1.32.1` (admin-fetch gate fix). `dist/` rebuilt.

### Backend
- `SecurityConfig`: `GET /feedback/accepted` is now `permitAll`; `POST /feedback` and
  `POST /feedback/{id}/upvote` are `authenticated()` (GUESTs allowed); `GET /feedback` and
  `PATCH /feedback/**` stay ADMIN.
- `FeedbackController`: public board reads the caller's optional id for `upvotedByMe`; `create` passes
  role + client IP; new `POST /feedback/{id}/upvote` toggle.
- `FeedbackService`: accepted board returns both types ordered by upvotes; per-list decoration of
  `upvotes`/`upvotedByMe` in two queries; guest submissions verify Turnstile (mirrors flood reports);
  `toggleUpvote`. New `FeedbackUpvote` entity + `FeedbackUpvoteRepository`. `Feedback` gains transient
  `upvotes`/`upvotedByMe`. `CreateFeedbackRequest` gains `captchaToken`.

### Database
- setup.sql appended: yes (idempotent — adds the `feedback` enums/table, previously migration-only,
  plus `feedback_upvote`, so a fresh build is self-contained)
- Migration: db/migrations/feat/feedback-upvotes/20260628_feedback_upvotes.up.sql
- Rollback:  db/migrations/feat/feedback-upvotes/20260628_feedback_upvotes.down.sql

### Config / Env
- None (reuses the existing `turnstile.secret-key`).

### Testing
- Backend `mvn compile` BUILD SUCCESS. Frontend `tsc -b` + `vite build` clean. Mock endpoints
  exercised via curl (board filtering/sort, upvote toggle on/off, anonymous upvote 401, guest submit
  pending). Apply the migration before deploying the backend.

## [feat/auto-center-on-load] — 2026-06-28

**Summary:** On load the map now centers on the user's device location automatically, instead of
opening on the default monitoring area and requiring a press of the floating locate button.

### Frontend
- `src/App.tsx` — the mount geolocation effect now also sets `mapFocus` on the first device fix
  (reusing the existing `FlyTo` path), so the map pans/zooms to the user automatically. Guarded by
  a `didAutoCenter` ref so it fires once; uses `setMapFocus((prev) => prev ?? …)` so it never
  overrides a place the user already searched/selected while the fix was converging. Silent on
  permission denial / no GPS (map stays at the default area; the locate button still works).
- UI version bumped `1.29.0 → 1.30.0`.
- `dist/` rebuilt and committed.

### Backend
- None.

### Database
- setup.sql appended: no
- Migration: none
- Rollback:  none

### Config / Env
- None.

### Testing
- `npm run typecheck` and `npm run build` pass.
- Manual: with location granted, the map auto-centers on load; with location denied it stays on the
  default area with no load-time error; an address searched immediately on load is not overridden.

---

## [feat/multi-area-flood-reports] — 2026-06-28

**Summary:** Allow a user to report a new flood in a *different* area while a prior report is still
active. Replaces the blanket "one active report per person" cooldown with a per-location rule so a
user who has driven on to another flooded street can report it, while same-spot duplicates are still
prevented.

### Frontend
- `src/utils/viewModel.ts` — added `REPORT_DISTINCT_M = 250` (m) and a pure helper
  `nearbyOwnActiveReport(enriched, loc, withinM?)` that returns the user's nearest still-active
  report within the threshold of a candidate location (reuses `kmBetween`).
- `src/App.tsx` —
  - `startReport`: replaced the `enriched.find((r) => r.mine && !r.expired)` blanket gate with the
    per-location check against the user's GPS; reworded the toast.
  - `pickSeverity`: re-checks the actual dropped pin (`pending`) before POST so dragging back onto a
    reported spot is still caught; and now surfaces intentional `409 / 429` server messages verbatim
    instead of the generic failure.
- UI version bumped `1.28.0 → 1.29.0`.
- `dist/` rebuilt and committed.

### Backend
- **PENDING / UNVERIFIED (action required).** If baha-api enforces its own "one active report per
  user" rule server-side, the relaxed frontend will still receive `409 / 429` on the 2nd report
  (the user now sees that server message). To deliver the feature end-to-end, verify baha-api's
  report-create behavior and, if it blocks, relax the rule to **per-location** (same ~250 m idea) or
  scope it so distinct coordinates are allowed for the same user within the active window. Track via
  the backend branch/migration flow once confirmed.

### Database
- setup.sql appended: no
- Migration: none (frontend-only as shipped; a backend rule change, if needed, will carry its own
  migration/rollback)
- Rollback:  none

### Config / Env
- None.

### Testing
- `npm run typecheck` and `npm run build` pass.
- Haversine threshold check: same-spot / ~120 m → blocked as duplicate; ~800 m / ~2 km → allowed.
- Against the json-server mock (always accepts POST) the frontend gate behaves: same-spot re-report
  is blocked with a "View" action; a report ~1 km away posts; dragging back within 250 m is blocked
  at submit time.
- Not yet exercised against real baha-api — see Backend note above.
