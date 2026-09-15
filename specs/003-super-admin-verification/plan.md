# Implementation Plan: Super Admin Clinic Verification

**Branch**: `003-super-admin-verification` | **Date**: 2026-09-02 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-super-admin-verification/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

A Super Admin (a single configuration-bootstrapped credential, HTTP Basic Auth, no database identity) lists unverified clinics and toggles `Clinic.verified` (001's entity) idempotently in either direction. A `true → false` transition publishes a `ClinicDeVerifiedEvent` for 008 to consume later — this feature does not implement the cascade itself.

## Technical Context

**Language/Version**: Java 21 (backend); TypeScript + React 18 (frontend) — same as 001/002.

**Primary Dependencies**: Spring Boot 3.x (Web, Data JPA, Validation, Security) — reuses 001's `ClinicRepository`; no new library needed (Basic Auth is built into Spring Security).

**Storage**: PostgreSQL — no new table. Reads/writes 001's existing `clinic.verified` column only.

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers (backend); Vitest + React Testing Library (frontend) — same as 001/002.

**Target Platform**: Linux container (Docker) — same as prior features.

**Project Type**: Web application (backend + frontend) — extends existing structure.

**Performance Goals**: Same order of magnitude as prior features (2s p95) — low-frequency admin action.

**Constraints**:
- No database-backed Super Admin identity — credentials from configuration only (FR-009).
- `verified` toggle MUST be idempotent (FR-007) — repeated calls in the same target state produce no duplicate side effects, notably no duplicate `ClinicDeVerifiedEvent`.
- The de-verification cascade itself is explicitly out of scope — only the event publication point is built here.

**Scale/Scope**: Single feature — 0 new entities, 2 endpoints (list pending, toggle verified).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Test-First Development | PASS | Tests for idempotency, authorization rejection, and event publication written before implementation. |
| II. Simplicity & YAGNI | PASS | No new entity, no new top-level module, no document-upload/review-queue scaffolding (explicitly out of scope). Reuses 001's `ClinicRepository` directly. |
| III. Modular, Library-First Architecture | PASS | Cascade trigger (FR-008) implemented as an explicit `ApplicationEvent`, not a direct call into 008's not-yet-built internals — exactly the event-driven cross-module pattern the constitution requires. |
| IV. Data Privacy & Integrity by Design | PASS | Super Admin credentials sourced from configuration, never persisted (FR-009) — no new identity data to protect, but the credential-handling itself follows the same "never log/expose a secret" discipline as password handling in 001/002. |

No violations — Complexity Tracking is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/003-super-admin-verification/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
└── tasks.md
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/cms/identity/admin/
│   ├── SuperAdminSecurityConfig.java       # Basic Auth filter chain for /api/v1/admin/**
│   ├── ClinicVerificationService.java      # list pending, verify, un-verify (+ event publish)
│   ├── ClinicDeVerifiedEvent.java          # published on true→false transition
│   └── ClinicVerificationController.java
└── src/test/java/com/cms/identity/admin/
    └── integration/

frontend/
├── src/features/clinic-verification/
│   ├── PendingClinicsList.tsx     # Pending/Verified tabbed list with verify/un-verify actions
│   └── api.ts
└── tests/
    └── clinic-verification/
```

**Structure Decision**: New package `com.cms.identity.admin` alongside 001's `com.cms.identity.clinic`/`account` — administrative behavior on the existing Clinic entity, not a new module (research.md).

## Post-Design Constitution Re-Check

Re-evaluated after Phase 1 (data model + contracts + quickstart, below): all four principles still PASS. The event-based cascade trigger and the idempotency design in data-model.md directly implement Principle III and FR-007/FR-008 — no new violations.

## Complexity Tracking

*No violations — table intentionally left empty.*
