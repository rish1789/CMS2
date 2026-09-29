# Quickstart: Dev Environment & Repository Hygiene

Validates the three user stories in [spec.md](spec.md) end-to-end. Requires Docker (or Docker Desktop) installed — this validation cannot run in this sandbox (confirmed Docker/Testcontainers limitation, see project memory `docker_testcontainers_sandbox_limitation`); run it in a real dev machine or CI.

## Prerequisites
- Docker with Compose v2 (`docker compose`, not the standalone `docker-compose` binary) available on PATH.
- A clone of this repository with this feature's changes applied.
- No process already bound to host port 5432 (or adjust before running, per the spec's documented edge case).

## Scenario 1 — One-command local database setup (US1, P1)

1. From the repo root: `docker compose up -d`
2. Confirm the container is healthy: `docker compose ps` — expect a `postgres` service in a running/healthy state.
3. Start the backend with no `DB_*` overrides: `cd backend && ./gradlew bootRun`
4. **Expected outcome**: the backend logs show a successful datasource connection and Flyway migrations applying cleanly (no `Connection refused` / auth failure), matching SC-001.
5. Tear down: `docker compose down` (add `-v` to also remove the data volume, if a clean slate is wanted for the next run).

## Scenario 2 — Clean, fully-gitignored working tree (US2, P2)

1. From the repo root: `cd backend && ./gradlew build && cd ../frontend && npm run build`
2. From the repo root: `git status`
3. **Expected outcome**: no untracked build output, `.gradle/` cache, IDE file, or `*.log` file appears in the output, matching SC-002.

## Scenario 3 — No stray tool-cache files (US3, P3)

1. From the repo root: `find backend/src -name hook.cache.json`
2. **Expected outcome**: zero results, matching SC-003.

## Zero-regression check (all stories)

1. `cd backend && ./gradlew compileJava compileTestJava`
2. `cd frontend && npm run build`
3. **Expected outcome**: both succeed exactly as they did before this feature, matching SC-004 — proving the feature introduced no application-behavior change.
