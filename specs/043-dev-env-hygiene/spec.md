# Feature Specification: Dev Environment & Repository Hygiene

**Feature Branch**: `043-dev-env-hygiene`

**Created**: 2026-09-15

**Status**: Draft

**Input**: User description: "backlog/040-dev-environment-repository-hygiene.md" — one-command local Postgres setup, a fully-gitignored working tree, and removal of stray `.impeccable/hook.cache.json` tool-cache files from source directories. First of a 13-feature "production hardening + UX redesign" wave (040-052) added after the original 39-feature backlog converged, scoped from a prior architect review (`PRODUCTION_ROADMAP.md`) and a direct 2026-09-15 codebase audit. No application behavior changes — pure environment/tooling.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - One-command local database setup (Priority: P1)

A developer (existing or new to the project) wants to start working on CMS2 locally and needs a Postgres database matching what the backend expects, without manually installing and configuring Postgres or hunting through `application.yml` to discover the expected database name/credentials.

**Why this priority**: This is the single biggest, most concrete gap identified in the prior architect review — no `docker-compose.yml` exists anywhere despite the backend depending on Postgres 16+ for both normal operation and Testcontainers-backed integration tests. It's also the item with the clearest, most testable "done" state.

**Independent Test**: Can be fully tested by running `docker compose up -d` (or the configured service name) at the repo root on a machine with Docker installed and nothing else configured, then confirming the backend connects successfully with zero additional environment overrides.

**Acceptance Scenarios**:

1. **Given** a fresh clone of the repository with Docker installed, **When** a developer runs the documented compose command, **Then** a Postgres instance starts with a database/username/password matching `application.yml`'s expected local defaults.
2. **Given** the Postgres service is running via this compose file, **When** the backend is started with no `DB_*` environment variable overrides, **Then** it connects successfully and Flyway migrations apply cleanly.

---

### User Story 2 - Clean, fully-gitignored working tree (Priority: P2)

A developer working in the repository wants confidence that `git status` never shows build output, IDE files, or log files as untracked-and-not-ignored, so that `git add`/`git commit` never risks accidentally including generated artifacts.

**Why this priority**: Lower risk than P1 (nothing currently breaks because of this gap — `backend/.gitignore` and `frontend/.gitignore` already cover their own subtrees), but still a real, confirmed gap at the repository root, and a quick, low-effort fix.

**Independent Test**: Can be fully tested by running a full backend build (`./gradlew build`) and frontend build (`npm run build`) at the repo root, then confirming `git status` shows no untracked build/cache/log/IDE files.

**Acceptance Scenarios**:

1. **Given** the updated root `.gitignore`, **When** a full backend and frontend build is run, **Then** `git status` reports a clean, fully-accounted-for working tree (no stray untracked build artifacts).

---

### User Story 3 - No stray tool-cache files inside source directories (Priority: P3)

A developer browsing `backend/src/main/java/com/cms/**` or `backend/src/test/java/com/cms/**` wants to see only real source files in each package directory, not tool-cache clutter mixed in alongside them.

**Why this priority**: Purely cosmetic/hygiene — these files are already gitignored and untracked, so they carry no correctness risk, only a browsing-friction cost. Lowest priority of the three.

**Independent Test**: Can be fully tested by running `find backend/src -name hook.cache.json` and confirming zero results.

**Acceptance Scenarios**:

1. **Given** the current 24 stray `.impeccable/hook.cache.json` files under `backend/src/**`, **When** this feature is complete, **Then** none remain on disk.

---

### Edge Cases

- What happens if a developer already has a local Postgres instance running on the default port? The compose file's port mapping should be documented clearly enough that a conflict is easy to diagnose (this is a dev-convenience tool, not something that needs elaborate conflict-avoidance logic).
- What happens if `.impeccable/` regenerates new `hook.cache.json` files after this cleanup (since the tool itself is still active)? That's expected and acceptable — they're already gitignored; this feature is a one-time working-tree cleanup, not a permanent prevention mechanism beyond the existing gitignore rule.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The repository MUST provide a `docker-compose.yml` at the repo root defining a single Postgres service.
- **FR-002**: The Postgres service's image, database name, username, and password MUST exactly match the local defaults already documented in `backend/src/main/resources/application.yml` and `.env.example` — no divergent configuration requiring env-var overrides to use it.
- **FR-003**: The root `.gitignore` MUST cover build output directories, IDE files, and log files at the repository root, without duplicating patterns already correctly scoped inside `backend/.gitignore` or `frontend/.gitignore`.
- **FR-004**: All `.impeccable/hook.cache.json` files currently present under `backend/src/main/java/**` and `backend/src/test/java/**` MUST be deleted from disk.
- **FR-005**: This feature MUST NOT modify any application source file, `application.yml`, or Flyway migration — it is strictly environment/tooling configuration.

### Key Entities

N/A — this feature introduces no data model changes.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A developer with Docker installed can go from a fresh clone to a running local Postgres instance matching backend expectations in a single command, with zero manual configuration steps.
- **SC-002**: `git status` on a fully-built working tree (backend + frontend) shows zero untracked build/cache/log/IDE artifacts at the repository root.
- **SC-003**: Zero `.impeccable/hook.cache.json` files exist under `backend/src/**` immediately after this feature ships.
- **SC-004**: `./gradlew compileJava compileTestJava` and `npm run build` both succeed unchanged after this feature — proving zero application-behavior impact.

## Assumptions

- Docker (or Docker-compatible tooling) is available in the target development environment where `docker-compose.yml` will actually be used — it is understood that this sandbox itself cannot run Docker (per the project's confirmed Testcontainers/Docker sandbox limitation), so verification of the compose file's actual runtime behavior happens in a real dev/CI environment, consistent with every other feature in this project's documented verification caveats.
- The Postgres defaults already documented in `.env.example` (`cms`/`cms`/`cms` per the prior architect review) remain the correct target values — this feature does not change what those defaults are, only makes them runnable via one command.
- No existing developer relies on a *different* local Postgres setup that this compose file would conflict with in a way requiring migration guidance — this is an additive convenience, not a replacement of any existing required setup step.
