# Changelog

All notable changes to FloodedArea (baha-ui + baha-api), most recent first.

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
