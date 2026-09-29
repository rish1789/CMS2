# Implementation Plan: Backend Security & Scale Hardening

**Branch**: `047-backend-hardening` | **Date**: 2026-09-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/047-backend-hardening/spec.md`

## Summary

Four independent hardening items: (1) cap `DiscoveryResultRepository.search`'s result size via `Pageable`, response stays a flat array (no frontend contract change); (2) gate `LoggingNotificationSender`'s recipient/message logging behind a new off-by-default `app.notification.log-pii` property; (3) extract the duplicated `30 * 60` literal in `WaitlistEntry`/`WaitlistMatchingService` into one named constant; (4) expand `SECURITY.md`'s existing Rate Limiting section with the full design rationale already documented in `RateLimitingFilter`'s own Javadoc, and prove the limiter engages live against the real running application (this sandbox has a real local Postgres reachable via `preview_start`, so this can be verified directly, not just left to a Testcontainers test this sandbox can't run).

## Technical Context

**Language/Version**: Java 21 / Spring Boot (unchanged).

**Primary Dependencies**: None new.

**Storage**: No schema change — `DiscoveryResult` is a projection DTO, not an entity.

**Testing**: Unit tests for the constant consolidation and log-gating; a live, real-application verification (via `preview_start`, this project's established workaround for running the app in this sandbox) for the rate limiter, since a true Testcontainers-backed integration test can't execute here.

**Target Platform**: Unchanged.

**Project Type**: Existing web application; backend-only changes across 3 existing modules (`discovery`, `notification`, `waitlist`) plus a `SECURITY.md` edit.

**Performance Goals**: Discovery search response time should improve or stay flat under the new cap (fewer rows fetched, not more).

**Constraints**: `DiscoverySearch.tsx`'s existing flat-array contract MUST NOT change (verified: `searchDiscovery` returns `Promise<DiscoveryResult[]>`); zero regression to 035/036/037/028/029's existing tests.

**Scale/Scope**: 4 edited files (`DiscoveryController.java`, `DiscoveryResultRepository.java`/`DiscoverySearchService.java`, `LoggingNotificationSender.java`, `WaitlistEntry.java`+`WaitlistMatchingService.java`) + `SECURITY.md`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Test-First)**: New unit tests for the discovery cap and the notification log-gate, written to fail against pre-fix behavior first where practical. The rate-limiter's existing unit test (`RateLimitingFilterTest`) already exists and passes; this feature adds a live-verification step (documented, not a new automated test this sandbox could run anyway) rather than duplicating Testcontainers-integration-test effort this environment can't execute.
- **Principle II (Simplicity & YAGNI)**: PASS — discovery keeps a flat-array response (no pagination UI/metadata nobody asked for); the log-gate is one boolean property, not a full logging-framework change; the constant consolidation touches only the 2 duplicated sites.
- **Principle III (Modular Architecture)**: N/A — no module-boundary changes, each fix stays inside its owning module.
- **Principle IV (Data Privacy & Integrity)**: Directly relevant to US2 (PII logging) — this feature closes a real privacy gap at design time, consistent with the principle's intent.

No violations. No Complexity Tracking entries needed.

## Project Structure

### Documentation (this feature)

```text
specs/047-backend-hardening/
├── plan.md
├── research.md
├── data-model.md    # N/A — no data model
├── quickstart.md
└── tasks.md
```

No `contracts/` — discovery's existing contract is unchanged (still a flat `DiscoveryResult[]`, only its size is now bounded); no new endpoint.

### Source Code (repository root)

```text
backend/src/main/java/com/cms/discovery/
├── DiscoveryController.java        # EDITED — accept page/size params
├── DiscoverySearchService.java     # EDITED — cap size, build Pageable
└── DiscoveryResultRepository.java  # EDITED — Pageable param on search()

backend/src/main/java/com/cms/notification/service/
└── LoggingNotificationSender.java  # EDITED — gate logging behind a property

backend/src/main/java/com/cms/waitlist/
├── domain/WaitlistEntry.java           # EDITED — use the new constant
└── service/WaitlistMatchingService.java # EDITED — use the new constant, NEW constant defined in one of these two (WaitlistEntry, since it's the domain owner)

SECURITY.md   # EDITED — expanded Rate Limiting section
```

**Structure Decision**: All edits stay inside their existing owning module (discovery/notification/waitlist), consistent with Principle III — no new module, no cross-module coupling introduced.

## Complexity Tracking

No Constitution Check violations — this section is not applicable.
