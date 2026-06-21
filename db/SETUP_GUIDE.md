# BahaWatch database setup

PostgreSQL 14+. Schema: `baha`. Application role: `baha_user`.

## From scratch (local)

```bash
# 1. Create the database (run as a superuser, e.g. postgres)
createdb baha

# 2. Structure → accounts → reference/demo data, in this order.
#    Run as a superuser so setup.sql can create the role + enable pgcrypto.
psql -d baha -f db/setup.sql
psql -d baha -f db/user_creation.sql
psql -d baha -f db/seed.sql
```

All three scripts are **idempotent** — safe to re-run. `setup.sql` creates the
`baha_user` login role (password `Password123!` for dev) and the `baha` schema;
`user_creation.sql` seeds the login accounts; `seed.sql` loads hotlines, the
alert ribbon, evacuation centers, and sample flood reports.

## Seeded login accounts

| Role    | Email                  | Password      |
|---------|------------------------|---------------|
| ADMIN   | `admin@bahawatch.ph`   | `Admin123!`   |
| CITIZEN | `citizen@bahawatch.ph` | `Citizen123!` |

Google-SSO accounts are created automatically on first sign-in (provider
`GOOGLE`, no password).

## Docker

`docker compose up` (from `baha-api/`) provisions Postgres and runs the three
scripts automatically on the first start (see `docker-compose.yml`).

## Per-branch migrations

Follow the appointments-api convention: put incremental, numbered, idempotent
migration scripts under `db/<branch-name-with-dashes>/NN_description.sql`, and
keep `setup.sql` + `seed.sql` current as the canonical fresh-install state.
