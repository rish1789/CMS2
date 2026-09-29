# Research: Dev Environment & Repository Hygiene

No `NEEDS CLARIFICATION` markers exist in this feature's Technical Context — it's a narrow, fully-precedented tooling addition. This document records the two small implementation decisions that still needed a deliberate choice.

## Decision 1: Postgres image and version pin

**Decision**: `postgres:16-alpine`, exposed on host port `5432`, with `POSTGRES_DB=cms`, `POSTGRES_USER=cms`, `POSTGRES_PASSWORD=cms`, and a named volume for data persistence across container restarts.

**Rationale**: `CLAUDE.md` and `backend/build.gradle`'s Testcontainers usage both require "PostgreSQL 16+." The `-alpine` variant is the standard lightweight choice for local dev (smaller pull, faster cold start, no feature difference relevant to this project's usage of plain SQL/JPA/Flyway). Port 5432 and the `cms`/`cms`/`cms` credentials are not a new decision — they're copied exactly from `application.yml`'s existing defaults and `.env.example`, per FR-002's explicit requirement that this feature introduce no divergent configuration.

**Alternatives considered**:
- Pinning an exact patch version (e.g. `postgres:16.4-alpine`) — rejected as unnecessary precision for a local-dev-only convenience file; the project's own stated requirement is "16+," and Flyway migrations are what actually need to be compatible, which they already are against any 16.x release.
- A multi-service compose file (adding pgAdmin or a seed-data init container) — rejected per Constitution Principle II (Simplicity/YAGNI); the spec asks only for "a Postgres instance," and no seed-data requirement exists in this feature's scope (seeding, if wanted, is a separate concern already handled ad hoc via the app's own registration/onboarding flows in local testing).

## Decision 2: Root `.gitignore` additions — scope and non-duplication

**Decision**: Add exactly four pattern groups to the root `.gitignore`: `build/` and `bin/` (top-level build output, in case any root-level tooling ever produces these — currently none does, but this closes the gap defensively at low cost), `.gradle/` (Gradle's own cache, in case a root-level Gradle wrapper invocation ever creates one outside `backend/`), IDE folders (`.idea/`, `*.iml`, `.vscode/`), and `*.log`. Do not touch or duplicate any pattern already present in `backend/.gitignore` or `frontend/.gitignore`.

**Rationale**: The verified gap (per the backlog grooming audit) is specifically that the *root* `.gitignore` has no coverage for these categories — `backend/.gitignore` and `frontend/.gitignore` already correctly scope their own subtrees. Duplicating those subtree patterns at the root would be redundant (a pattern in a nested `.gitignore` already applies to paths under it) and would make future maintenance confusing (two files claiming to govern the same path). The root file's job is only to catch root-scope artifacts neither subtree file can see.

**Alternatives considered**:
- A single root `.gitignore` replacing both subtree files — rejected; out of scope (FR-005 forbids touching files beyond what's needed to close the confirmed gap) and would be a much larger, riskier change than the actual problem calls for.
