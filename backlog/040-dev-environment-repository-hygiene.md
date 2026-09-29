# 040 — Dev Environment & Repository Hygiene

**Module:** Cross-Cutting / Tooling
**Status:** Ready for spec-kit intake

## User Story
As a developer (or a new contributor) working on CMS2, I want a one-command local database setup and a clean, fully-gitignored working tree, so that I can get a working environment quickly and never accidentally commit build artifacts or tool cache files.

## Context
`PRODUCTION_ROADMAP.md` (Principal Architect review, 2026-09-14) identified this as Phase 1 of a 5-phase hardening plan. A git repository now exists (added in a later session, see `HANDOFF.md` Part 4), but several Phase 1 items were never completed. This is pure environment/tooling work — no application behavior changes.

Verified current gaps (2026-09-15 audit):
- No `docker-compose.yml` anywhere in the repo, despite the backend depending on Postgres 16+ and Testcontainers for integration tests. A new contributor has no one-command way to stand up a local database matching `application.yml`'s defaults.
- Root `.gitignore` only covers `.env*` and `.impeccable/` — it does not cover `build/`, `.gradle/`, IDE folders, or `*.log` at the repository root (backend/frontend each have their own `.gitignore` covering their own subtrees, which is fine and should not be duplicated, but root-level tooling output has no coverage).
- 24 `.impeccable/hook.cache.json` files are physically present inside `backend/src/main/java/**` and `backend/src/test/java/**` package directories — tool-cache artifacts sitting alongside real source files, cluttering module directories even though they're gitignored and untracked.

## Business Rules
- `docker-compose.yml` MUST define a single Postgres service whose image version, database name, username, and password exactly match `backend/src/main/resources/application.yml`'s documented local defaults (`cms`/`cms`/`cms` per the roadmap) — no divergent config that would require developers to override env vars just to use it.
- No behavior change to any application code — this feature touches only `.gitignore`, `docker-compose.yml`, and deletion of stray cache files. Nothing here should require a test beyond a manual verification step.
- `.impeccable/hook.cache.json` files under `backend/src/**` MUST be removed from disk (they are already gitignored, so this is a working-tree cleanup, not a git operation).
- Root `.gitignore` additions must not conflict with or duplicate `backend/.gitignore` / `frontend/.gitignore` — only add root-scope patterns not already covered by a subtree's own file.

## Acceptance Criteria
- Given a fresh clone of the repository with Docker installed, when a developer runs `docker compose up -d db` (or equivalent service name) at the repo root, then a Postgres instance matching `application.yml`'s expected connection details starts and the backend can connect to it with zero additional configuration.
- Given the current working tree, when `find backend/src -name hook.cache.json` is run after this feature, then it returns zero results.
- Given the root `.gitignore` after this feature, when a build is run (`./gradlew build`, `npm run build`) at the repo root, then no build output, `.gradle/` cache, IDE file, or `*.log` file shows up as untracked-and-not-ignored in `git status`.
- Given `backend compileJava compileTestJava` and `npm run build` (frontend), when run after this change, then both still succeed — this feature must be a no-op on application behavior.

## Dependencies
- None — this is a standalone, foundational cleanup with no dependency on any of the 39 converged features.
- Should be done before 041 (CI/CD Quality Gates), since a working local Postgres setup makes it easier to verify CI changes locally first.

## Explicitly Out of Scope
- Any change to `application.yml`, Flyway migrations, or backend/frontend source code.
- Production deployment configuration (this `docker-compose.yml` is a local-dev convenience only, not a deployment artifact).
- Removing or modifying the `.impeccable/` tool itself — only its stray cached output files under `backend/src` are cleaned up.

## Source References
- `PRODUCTION_ROADMAP.md` §1.1, §1.6, §3 Phase 1 (items 2-4)
- Verified against current repository state via direct inspection, 2026-09-15 (24 cache files confirmed present, no docker-compose.yml found, root .gitignore confirmed incomplete)
