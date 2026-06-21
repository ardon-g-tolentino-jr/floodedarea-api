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
| GET    | `/healthcheck`           | public   | Liveness                             |
```
