# Changelog

All notable changes to FloodedArea (baha-ui + baha-api), most recent first.

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
