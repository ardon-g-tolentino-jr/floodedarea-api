# baha-api

BahaWatch REST API — crowd-sourced flood reports, evacuation centers, emergency
hotlines, rainfall alerts, and SOS rescue requests for Metro Manila & Rizal.

Spring Boot 3.3.5 · Java 17 · PostgreSQL · self-contained JWT auth (Basic login
+ Google SSO) with `CITIZEN` / `ADMIN` roles · SSE live-activity stream · per-IP
rate limiting. Mirrors the structure of `appointments-api`.

## Run locally

```bash
# 1. Postgres up + schema/accounts/seed loaded (see db/SETUP_GUIDE.md)
createdb baha
psql -d baha -f db/setup.sql -f db/user_creation.sql -f db/seed.sql

# 2. Start the API (dev profile has working local defaults)
mvn spring-boot:run
# → http://localhost:8083
```

Set `GOOGLE_CLIENT_ID` (or `google.client-id` in `application-dev.properties`)
to the same OAuth Web client id the UI uses to enable Google SSO. Basic login
works without it.

## Endpoints

| Method | Path                     | Access   | Purpose                              |
|--------|--------------------------|----------|--------------------------------------|
| POST   | `/auth/register`         | public   | Citizen sign-up (auto-login)         |
| POST   | `/auth/login`            | public   | Basic login                          |
| POST   | `/auth/google`           | public   | Google SSO (verifies the ID token)   |
| GET    | `/auth/me`               | auth     | Current session                      |
| GET    | `/reports`               | public   | Active flood reports (`?severity=`)  |
| POST   | `/reports`               | auth     | Submit a flood report                |
| POST   | `/reports/{id}/confirm`  | auth     | Confirm a report (+1)                |
| PATCH  | `/reports/{id}`          | admin    | Resolve / reactivate                 |
| DELETE | `/reports/{id}`          | admin    | Remove                               |
| GET    | `/centers`               | public   | Evacuation centers                   |
| POST/PATCH/DELETE | `/centers/**` | admin    | Manage centers                       |
| GET    | `/hotlines`              | public   | Emergency hotlines                   |
| POST/PATCH/DELETE | `/hotlines/**`| admin    | Manage hotlines                      |
| GET    | `/alerts`                | public   | Active alert ribbon                  |
| POST/PATCH/DELETE | `/alerts/**` | admin    | Manage alerts                        |
| POST   | `/rescue`                | auth     | Send an SOS rescue request           |
| GET    | `/rescue`                | admin    | Rescue queue (`?status=`)            |
| PATCH  | `/rescue/{id}`           | admin    | Advance status                       |
| GET    | `/sse/activity`          | public   | Live-activity SSE stream             |
| GET    | `/social-posts`          | admin    | Auto-post attempts (`?limit=`)       |
| POST   | `/social-posts/{id}/retry` | admin  | Re-queue a failed/skipped post       |
| GET    | `/healthcheck`           | public   | Liveness                             |

## Social auto-post

When an **admin** submits a system flood report, the UI's incident modal can tick Facebook and X;
`POST /reports` then carries `socialNetworks` + `socialCaption`, and after the transaction commits
the API publishes to the FloodedArea Page and X account asynchronously. Citizen reports are never
auto-posted — the gate is `system && role == ADMIN` in `FloodReportService`.

Every attempt is one `social_post` row (`pending` → `sent` / `failed` / `skipped`), listed in the
Admin console with a link to the live post and a **Retry** button.

Two independent layers control it:

| Layer | Where | Effect |
|---|---|---|
| Deployment | `SOCIAL_ENABLED`, `SOCIAL_DRY_RUN` env | Master on/off. Needs a restart. |
| Operations | `app_setting` keys `social_autopost_facebook` / `social_autopost_x` | Per-network kill switch, toggled live from the Admin console. No deploy. |

Both default to the safe setting: publishing is off, and dry-run logs the payload and marks the row
`skipped` rather than calling the network. Copy `.env.example` → `.env` and fill in:

- `FB_PAGE_ID` — the Page's numeric id.
- `FB_PAGE_TOKEN` — a **long-lived** Page access token with `pages_manage_posts` and
  `pages_read_engagement`. Short-lived tokens expire in about an hour.
- `X_API_KEY` / `X_API_SECRET` / `X_ACCESS_TOKEN` / `X_ACCESS_SECRET` — the developer app's
  consumer pair plus an access token for the posting account. The app must be set to
  **Read and Write**; a read-only token fails every post with a 403.
- `SOCIAL_API_BASE_URL` — the public base URL of *this API*, not the site. Facebook fetches the
  report photo from `<SOCIAL_API_BASE_URL><photoUrl>`, so **Facebook posts fail permanently while
  it is blank**. In the standard nginx deploy that is `https://floodedarea.com/api`.

Recommended rollout: set the tokens with `SOCIAL_ENABLED=true` and `SOCIAL_DRY_RUN=true`, post one
test incident, read the caption in the Admin console, then set `SOCIAL_DRY_RUN=false` and restart.
