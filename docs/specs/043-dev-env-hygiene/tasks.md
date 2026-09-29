---

description: "Task list for Dev Environment & Repository Hygiene"
---

# Tasks: Dev Environment & Repository Hygiene

**Input**: Design documents from `/specs/043-dev-env-hygiene/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md (N/A), quickstart.md

**Tests**: Not applicable — this feature has no application behavior to unit/integration test (see plan.md's Constitution Check, Principle I). Verification is the spec's own measurable Success Criteria, executed manually per quickstart.md.

**Organization**: All three user stories are fully independent of each other (no shared files, no ordering dependency) — they can be done in any order or in parallel.

## Phase 1: Setup

No setup phase needed — this feature requires no new tooling/dependencies beyond what the repository already assumes (Docker for US1, git for US2/US3).

## Phase 2: Foundational

No foundational/blocking phase needed — none of the three user stories depend on shared new infrastructure.

---

## Phase 3: User Story 1 - One-command local database setup (Priority: P1) 🎯 MVP

**Goal**: A developer can start a local Postgres instance matching `application.yml`'s expected defaults with a single command.

**Independent Test**: Run `docker compose up -d` at the repo root, then start the backend with no `DB_*` overrides and confirm it connects and Flyway migrations apply.

### Implementation for User Story 1

- [X] T001 [US1] Create `docker-compose.yml` at the repo root defining a single `db` service using image `postgres:16-alpine`, with `POSTGRES_DB=cms`, `POSTGRES_USER=cms`, `POSTGRES_PASSWORD=cms`, port mapping `5432:5432`, and a named volume (e.g. `cms-postgres-data`) for data persistence.
- [X] T002 [US1] Add a short usage comment block at the top of `docker-compose.yml` documenting the exact command (`docker compose up -d`) and noting it matches `.env.example`'s `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` defaults with zero required overrides.
- [ ] T003 [US1] Manually verify per `quickstart.md` Scenario 1: start the compose service, then run the backend with no `DB_*` env vars set, and confirm a successful datasource connection and clean Flyway migration run (requires Docker — attempted 2026-09-15: Docker CLI v29.3.1 is present but the daemon/engine is not running in this environment, `failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine`; re-run once Docker Desktop is started, consistent with this project's confirmed Testcontainers/Docker sandbox limitation).

**Checkpoint**: User Story 1 is fully functional and independently verified — a fresh clone + `docker compose up -d` + `gradlew bootRun` works with zero manual DB configuration.

---

## Phase 4: User Story 2 - Clean, fully-gitignored working tree (Priority: P2)

**Goal**: The root `.gitignore` covers build output, IDE files, and log files at the repository root, without duplicating `backend/.gitignore`/`frontend/.gitignore`'s own subtree coverage.

**Independent Test**: Run a full backend and frontend build, then confirm `git status` shows no untracked build/cache/log/IDE artifacts.

### Implementation for User Story 2

- [X] T004 [P] [US2] Edit the root `.gitignore` to add `build/`, `bin/`, `.gradle/`, `.idea/`, `*.iml`, `.vscode/`, and `*.log` patterns, placed in a clearly labeled section distinct from the existing `.env`/`.impeccable/` rules already there.
- [X] T005 [US2] Manually verify per `quickstart.md` Scenario 2: run `./gradlew compileJava compileTestJava` (backend) and `npm run build` (frontend), then confirm `git status` reports no untracked build/cache/log/IDE files at the repo root (depends on T004) — verified 2026-09-15, both builds succeeded and `git status --porcelain` showed only the intentional new backlog/spec files, zero stray build artifacts.

**Checkpoint**: User Story 2 is fully functional and independently verified — a full build leaves a clean `git status`.

---

## Phase 5: User Story 3 - No stray tool-cache files inside source directories (Priority: P3)

**Goal**: Zero `.impeccable/hook.cache.json` files remain under `backend/src/main/java/**` or `backend/src/test/java/**`.

**Independent Test**: Run `find backend/src -name hook.cache.json` and confirm zero results.

### Implementation for User Story 3

- [X] T006 [P] [US3] Delete all `.impeccable/hook.cache.json` files currently present under `backend/src/main/java/**` and `backend/src/test/java/**` (confirmed 24 files as of 2026-09-15 backlog grooming audit — re-enumerate with `find backend/src -name hook.cache.json` at implementation time, since the count may have changed). Re-enumerated immediately before deletion: still 24. Deleted, plus the now-empty `.impeccable/` directories left behind.
- [X] T007 [US3] Verify per `quickstart.md` Scenario 3: `find backend/src -name hook.cache.json` returns zero results (depends on T006) — verified 2026-09-15, zero results.

**Checkpoint**: User Story 3 is fully functional and independently verified — no tool-cache clutter remains in source directories.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Prove the feature introduced zero application-behavior regression, per FR-005 and SC-004.

- [X] T008 Run `cd backend && ./gradlew compileJava compileTestJava` and confirm it succeeds unchanged (zero-regression check, SC-004) — verified 2026-09-15, `BUILD SUCCESSFUL`.
- [X] T009 [P] Run `cd frontend && npm run build` and confirm it succeeds unchanged (zero-regression check, SC-004) — verified 2026-09-15, built successfully (142 modules transformed).
- [X] T010 Update `backlog/progress.md`'s row for `040-dev-environment-repository-hygiene` from "Not Started" to "Converged" (or the furthest stage actually reached), with a brief note per this project's established progress-tracking convention.

---

## Dependencies & Execution Order

### Phase Dependencies

- No Setup or Foundational phase — all three user stories can start immediately and in any order.
- Polish (Phase 6) depends on all three user stories being complete (it verifies their combined zero-regression impact).

### User Story Dependencies

- **User Story 1 (P1)**: No dependencies on US2/US3.
- **User Story 2 (P2)**: No dependencies on US1/US3.
- **User Story 3 (P3)**: No dependencies on US1/US2.

### Within Each User Story

- T001 → T002 → T003 (US1: create file, document it, then verify).
- T004 → T005 (US2: edit gitignore, then verify).
- T006 → T007 (US3: delete files, then verify).

### Parallel Opportunities

- T004 (US2) and T006 (US3) can run in parallel with each other and with US1's T001, since all three touch entirely different files.
- T008 and T009 (Polish) can run in parallel.

---

## Parallel Example

```bash
# All three user stories' first implementation task can run together:
Task: "Create docker-compose.yml at repo root (US1, T001)"
Task: "Edit root .gitignore (US2, T004)"
Task: "Delete stray .impeccable cache files (US3, T006)"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

Given the roadmap explicitly named the missing `docker-compose.yml` as the single biggest, most concrete gap, User Story 1 alone (T001-T003) is a meaningful standalone MVP even if US2/US3 were deferred.

### Incremental Delivery

All three stories are small and independent enough to implement together in one pass, then verify each independently via quickstart.md before moving to Polish.

## Notes

- No test tasks generated — this feature has no application behavior to test (see Tests note above); each user story's own manual verification step against quickstart.md is its correctness proof.
- Total: 10 tasks (3 + 2 + 2 + 3), all mapped to a specific file path or verification command.
