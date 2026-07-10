# Spec — feat/system-report (baha-api)

Admin-logged authoritative flood reports ("system reports") from news/other sources: an admin
posts at **any** location worldwide, optionally with a screenshot, and the report is tagged
`reporterTrust = 'system'`. Pairs with baha-ui v1.36.0 (new "🗞 Report incident" admin modal +
social share). Builds on the already-merged `feat/report-photo` (photo column + `/reports/:id/photo`).

## Contract (what baha-ui already sends/expects)

- `POST /reports` body MAY include `"system": true`. The backend honors it **only for ADMIN
  callers** → sets `reporter_trust = 'system'`; for non-admins it is ignored (stays
  verified/guest). `photoDataUrl` (the screenshot) rides the same body as for any report.
- For a `system` report, the backend **skips the geofence coverage check** so news-sourced
  incidents anywhere (e.g. Cebu, not just Metro Manila & Rizal) are accepted instead of `422`.
- Reports come back with `reporterTrust: "system"`; the UI renders a "🛈 System report" badge.

## Database

Migration `db/migrations/feat/system-report/20260710_system_report.up.sql` (+ `.down.sql`, + the
appended block in `db/setup.sql`): the `reporter_trust` column already exists (from
`feat/guest-reporting`); this only **widens its CHECK** to a third value:

```sql
ALTER TABLE flood_report DROP CONSTRAINT IF EXISTS flood_report_reporter_trust_check;
ALTER TABLE flood_report ADD CONSTRAINT flood_report_reporter_trust_check
    CHECK (reporter_trust IN ('verified','guest','system'));
```

No new column, no index. Rollback reclassifies any `system` rows to `verified` before restoring
the two-value CHECK (noted in the file).

## Code changes

### 1. `dto/CreateReportRequest.java` — accept the flag
Add a trailing optional field to the record (Boolean so absent = null = false):

```java
        @Size(max = 1_400_000, message = "Photo is too large.") String photoDataUrl,  // from feat/report-photo
        /** Admin-only: tag this as an authoritative "system" report (news/other sources). */
        Boolean system
```

### 2. `service/FloodReportService.java` — gate the flag on ADMIN; bypass geofence for system reports
`create(...)` already receives `UserRole role`. Two edits:

**(a) Skip the geofence check for admin system reports** (so "anywhere" posts). Change the guard
at ~line 62 to exempt them:

```java
        boolean isSystem = Boolean.TRUE.equals(req.system()) && role == UserRole.ADMIN;
        // System reports (admin, from news/other sources) may be anywhere — skip the coverage check.
        if (!isSystem && geofence.isEnabled() && !geofence.contains(req.latitude(), req.longitude())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "That location is outside the coverage area (Metro Manila & Rizal).");
        }
```

**(b) Set the trust tier** where `reporterTrust` is assigned (~line 84), extending the existing
guest/verified logic:

```java
        // 'system' = admin-logged authoritative report; else guest vs verified as before.
        r.setReporterTrust(isSystem ? "system" : (role == UserRole.GUEST ? "guest" : "verified"));
```

No other change: the entity's `reporter_trust` column already serializes as `reporterTrust`, and
the photo handling from `feat/report-photo` applies unchanged.

### 3. Security — no change
`POST /reports` is already `authenticated()`; the `system` flag is role-gated inside the service
(`role == UserRole.ADMIN`), so a non-admin can't forge a system report.

## Testing

1. `psql "$DB_URL" -f db/migrations/feat/system-report/20260710_system_report.up.sql`.
2. As an **ADMIN**, `POST /reports { …, "system": true }` at a location **outside** the geofence
   (e.g. Cebu) with a `photoDataUrl` → `201`, response `reporterTrust: "system"`, photo stored.
3. As a **CITIZEN/GUEST**, `POST /reports { …, "system": true }` → the flag is ignored
   (`reporterTrust` is `verified`/`guest`) and the geofence still applies.
4. `GET /reports` → the system report carries `reporterTrust:"system"` and its `photoUrl`.
5. Rollback: `…/20260710_system_report.down.sql` (system rows → verified, CHECK restored).

## CHANGELOG entry (paste at the top of `documentations/CHANGELOG.md`)

```markdown
## [feat/system-report] — 2026-07-10

**Summary:** Let an admin log a flood incident from news/other sources at any location worldwide,
attach a screenshot, tag it as an authoritative "system report", and share it to social media.
Backend adds the 'system' trust tier and a geofence bypass for it; frontend shipped in v1.36.0.

### Frontend
- baha-ui v1.36.0: admin-only "🗞 Report incident" modal (worldwide location, severity/depth,
  optional screenshot, source note), "🛈 System report" badge, and post-to-social-media share of
  the new report (Facebook/X/WhatsApp/Telegram/Email + native photo share).

### Backend
- `CreateReportRequest`: optional `system` flag.
- `FloodReportService.create`: honors `system` only for ADMIN → `reporterTrust='system'`, and
  skips the geofence coverage check for system reports (so they post anywhere).
- No SecurityConfig change.

### Database
- setup.sql appended: yes · Migration: db/migrations/feat/system-report/20260710_system_report.up.sql
  · Rollback: db/migrations/feat/system-report/20260710_system_report.down.sql
- `flood_report.reporter_trust` CHECK widened to `('verified','guest','system')`. No new column/index.

### Config / Env
- None.

### Testing
- Migration applied; admin POST system report outside the geofence (stored 'system' + photo),
  non-admin flag ignored + geofence enforced, GET returns reporterTrust:"system", rollback verified.
```
