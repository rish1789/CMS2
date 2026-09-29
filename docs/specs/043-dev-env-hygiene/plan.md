# Implementation Plan: Dev Environment & Repository Hygiene

**Branch**: `043-dev-env-hygiene` | **Date**: 2026-09-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/043-dev-env-hygiene/spec.md`

## Summary

Add a root `docker-compose.yml` providing one-command local Postgres matching `application.yml`'s existing defaults, complete the root `.gitignore` with build/IDE/log patterns not already covered by `backend/.gitignore`/`frontend/.gitignore`, and delete the 24 stray `.impeccable/hook.cache.json` tool-cache files currently sitting inside `backend/src/main/java/**` and `backend/src/test/java/**`. Pure environment/tooling addition — zero application source, `application.yml`, or migration changes.

## Technical Context

**Language/Version**: N/A for this feature's own artifacts (YAML config + `.gitignore` text + filesystem cleanup); existing project stack is Java 21 (backend) / TypeScript+React 18 (frontend), unchanged.

**Primary Dependencies**: Docker Compose (the project already requires Docker for backend Testcontainers integration tests — this feature adds no *new* external dependency, just a config file for one already assumed).

**Storage**: PostgreSQL 16+ (matches `backend/src/main/resources/application.yml`'s existing `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` defaults: `jdbc:postgresql://localhost:5432/cms`, `cms`, `cms`, per `.env.example`).

**Testing**: No automated test suite applies to a Compose YAML file or `.gitignore` — verification is manual (`docker compose up`, then a real backend connection attempt) plus `gradle compileJava compileTestJava` / `npm run build` as a zero-regression check, per the spec's own Success Criteria.

**Target Platform**: Local developer machines (cross-platform — this repo is worked on from both Windows and the Linux-based GitHub Actions CI runner; the compose file must not assume a POSIX-only toolchain).

**Project Type**: Existing web application (Option 2: backend + frontend) — this feature adds root-level tooling, no new backend/frontend source directories.

**Performance Goals**: N/A.

**Constraints**: MUST NOT change any value in `application.yml`, MUST NOT require any `DB_*` env var override to work with the compose file's defaults, MUST NOT touch application source code.

**Scale/Scope**: One new file (`docker-compose.yml`), one edited file (root `.gitignore`), 24 known file deletions (`.impeccable/hook.cache.json` under `backend/src/**`).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Test-First, NON-NEGOTIABLE)**: Not triggered — this feature adds no backend logic and no Flyway migration, the two categories the principle explicitly names. A Compose file and `.gitignore` are not "new behavior" in the sense the principle governs (there is no business rule to regress). Verification instead relies on the spec's own measurable Success Criteria (manual compose-up check, `git status` cleanliness, zero-cache-file check, unchanged build success) — explicitly justified inapplicability, not a silent skip.
- **Principle II (Simplicity & YAGNI)**: PASS — one Postgres service, no extra services (no pgAdmin, no seed-data container) not asked for by the spec; gitignore additions are the minimum needed to close the confirmed gap, not a speculative catch-all.
- **Principle III (Modular, Library-First Architecture)**: N/A — no application module touched.
- **Principle IV (Data Privacy & Integrity by Design)**: N/A — no patient/clinical data, no schema change, no migration.

No violations. No Complexity Tracking entries needed.

## Project Structure

### Documentation (this feature)

```text
specs/043-dev-env-hygiene/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md         # Phase 1 output (N/A — no data model; documented explicitly)
├── quickstart.md        # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit-tasks — not created here)
```

No `contracts/` directory — this feature exposes no API, CLI, or UI contract to any consumer. `docker-compose.yml` is a local-dev convenience file, not an interface other code depends on; skipped per the plan template's own "skip if project is purely internal" guidance.

### Source Code (repository root)

```text
docker-compose.yml        # NEW — single Postgres 16 service, port 5432, db/user/pass = cms/cms/cms
.gitignore                 # EDITED — add build/, .gradle/, IDE, *.log patterns at root scope
backend/src/main/java/**/.impeccable/hook.cache.json   # DELETED (multiple, ~24 files)
backend/src/test/java/**/.impeccable/hook.cache.json   # DELETED (subset of the above)
```

No changes to `backend/src/main/java/com/cms/**` application code, `backend/src/main/resources/application.yml`, `frontend/src/**`, or any Flyway migration.

**Structure Decision**: This is a root-level, cross-cutting tooling addition to the existing backend+frontend web application (already `backend/` + `frontend/` at repo root, per `CLAUDE.md`). No new source directory is introduced; the only new file lives at the repo root alongside the existing `.env.example`, `README.md`, and `dev.sh`.

## Complexity Tracking

No Constitution Check violations — this section is not applicable.
