# Spec — feat/report-ttl (baha-api)

Admin-defined lifetime + source report time for **system reports**. Pairs with baha-ui v1.37.0.
Persist and echo two nullable fields; the **client owns the TTL math** (`enrichReports` anchors the
freshness countdown on `reportedAt` and uses `ttlMinutes` as a per-report override of the
severity-scaled default). Layers onto the pending `feat/report-photo` + `feat/system-report` Java.

## Contract (what baha-ui already sends/expects)

- `POST /reports` body MAY include (admin/system reports only):
  - `reportedAt` — ISO datetime when the source reported the flood (baha-ui derives it from
    "reported N hours ago"; defaults to now).
  - `ttlMinutes` — how long the report stays live (60/180/360/720/1440/2880; omitted ⇒ severity default).
- Reports come back with `reportedAt` and `ttlMinutes`; the UI computes expiry as
  `reportedAt + ttlMinutes` and hides reports past it. No server-side expiry filtering is required
  (matches today's client-side freshness model — the API returns `status='active'` rows).

## Database

Migration `db/migrations/feat/report-ttl/20260710_report_ttl.up.sql` (+ `.down.sql`, + `setup.sql`
append), idempotent:
```sql
ALTER TABLE flood_report ADD COLUMN IF NOT EXISTS reported_at TIMESTAMPTZ;
ALTER TABLE flood_report ADD COLUMN IF NOT EXISTS ttl_minutes INTEGER;
-- CHECK (ttl_minutes IS NULL OR ttl_minutes > 0)
-- v_active_flood_report recreated to include reported_at, ttl_minutes
```
Both nullable; citizen reports leave them unset. No index (not filtered/joined server-side).

## Code changes

### 1. `dto/CreateReportRequest.java` — accept the two fields
```java
import java.time.OffsetDateTime;
// ...append record components (nullable — admin-only optionals):
        OffsetDateTime reportedAt,
        Integer ttlMinutes
```

### 2. `entity/FloodReport.java` — map the columns + default reportedAt
```java
    @Column(name = "reported_at")
    private OffsetDateTime reportedAt;

    @Column(name = "ttl_minutes")
    private Integer ttlMinutes;
```
In `@PrePersist onCreate()`, default `reportedAt` to the create time so every report has one:
```java
        if (reportedAt == null) reportedAt = createdAt;
```
The entity serializes straight to JSON, so both appear as `reportedAt`/`ttlMinutes` in the API
response consumed by the frontend `FloodReport`.

### 3. `service/FloodReportService.java` — set them for ADMIN system reports (~line 84)
Reuse the `isSystem` gate from `feat/system-report`:
```java
        if (isSystem) {
            if (req.reportedAt() != null) r.setReportedAt(req.reportedAt());
            if (req.ttlMinutes() != null && req.ttlMinutes() > 0) r.setTtlMinutes(req.ttlMinutes());
        }
```
Non-admin/citizen reports ignore the fields (reportedAt then defaults to createdAt via `@PrePersist`).

### 4. Security — no change
`POST /reports` is already `authenticated()`; the fields only take effect for ADMIN system reports.

### (Optional) server-side cleanup
Not required — the client hides TTL-expired reports. If you want them auto-resolved server-side,
add a `@Scheduled` job that sets `status='resolved'` where
`now() > COALESCE(reported_at, created_at) + make_interval(mins => COALESCE(ttl_minutes, <severity default>))`,
modeled on `RoadStatusService`'s freshness approach.

## Testing

1. `psql "$DB_URL" -f db/migrations/feat/report-ttl/20260710_report_ttl.up.sql`.
2. As ADMIN, `POST /reports { …, "system": true, "reportedAt": <5h ago>, "ttlMinutes": 360 }` →
   `201`, response echoes both fields.
3. `GET /reports` → the report carries `reportedAt` and `ttlMinutes: 360`; the UI shows
   "Reported 5 hrs ago" and "Expires in ~1h".
4. Citizen `POST /reports` (no fields) → `reportedAt` defaults to `createdAt`, `ttlMinutes` null;
   behavior unchanged.
5. Rollback: `…/20260710_report_ttl.down.sql` (view restored, columns dropped).

## CHANGELOG entry (paste at the top of `documentations/CHANGELOG.md`)

```markdown
## [feat/report-ttl] — 2026-07-10

**Summary:** Let an admin define how long a system report stays live and record when the flood was
reported by the source; the countdown runs from the reported time. Frontend shipped in v1.37.0.

### Frontend
- baha-ui v1.37.0: "Report incident" modal gains "reported N hours ago" + a "keep live for" TTL
  preset; expiry is computed as reportedAt + TTL client-side, shown as a duration, with a
  "Reported X ago · from source" line on the detail card.

### Backend
- `CreateReportRequest`: optional `reportedAt` + `ttlMinutes`.
- `FloodReport` entity: `reported_at` / `ttl_minutes` columns; `@PrePersist` defaults reportedAt=createdAt.
- `FloodReportService.create`: sets both for ADMIN system reports; ignored otherwise.
- No SecurityConfig change. Server-side TTL cleanup optional (client hides expired reports).

### Database
- setup.sql appended: yes · Migration: db/migrations/feat/report-ttl/20260710_report_ttl.up.sql
  · Rollback: db/migrations/feat/report-ttl/20260710_report_ttl.down.sql
- `flood_report`: `+reported_at TIMESTAMPTZ`, `+ttl_minutes INTEGER` (CHECK > 0);
  `v_active_flood_report` recreated to include them. No index.

### Config / Env
- None.

### Testing
- Migration applied; admin POST with reportedAt+ttlMinutes echoed and stored; citizen defaults
  unchanged; GET returns both fields; rollback verified.
```
