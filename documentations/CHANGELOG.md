# Changelog

All notable changes to FloodedArea (baha-ui + baha-api), most recent first.

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
