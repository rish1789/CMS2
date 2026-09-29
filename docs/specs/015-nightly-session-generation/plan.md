# Implementation Plan: Nightly Rolling Session Generation (15-Day Horizon)

**Branch**: `015-nightly-session-generation` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/015-nightly-session-generation/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

A new `Session` entity (first defined here, `com.cms.scheduling`) plus `SessionGenerationService.generate(LocalDate runDate)`, which for every existing `Schedule` computes the 15-day horizon, finds which of its applicable dates don't yet have a Session, and creates them — each Session snapshotting its parent Schedule's mode/time-range/slot-interval so it's immune to a later Schedule edit. Wired to two triggers: a real nightly `@Scheduled` cron job, and a Super-Admin-only `POST /api/v1/admin/sessions/generate` endpoint under the existing admin security chain. No Slot entity, no Slot logic — strictly Session rows.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript/React (frontend, a minimal Super Admin trigger button, mirroring `PendingClinicsList`'s existing `AdminCredentials`/Basic-Auth pattern).

**Primary Dependencies**: Spring Boot 3.x (Data JPA, Scheduling — `@EnableScheduling`/`@Scheduled`) — reuses `com.cms.scheduling.Schedule`/`ScheduleRepository` (009) directly, and the existing `SuperAdminSecurityConfig`/`/api/v1/admin/**` chain (003) with no security-config change needed (already `anyRequest().authenticated()`). No new external dependency.

**Storage**: PostgreSQL — one new migration (`V8`): `session` table with a unique `(schedule_id, session_date)` constraint.

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers + MockMvc (backend). Frontend: a small addition mirroring `PendingClinicsList.test.tsx`'s existing pattern.

**Target Platform**: Linux container (Docker).

**Project Type**: Extension of the existing `com.cms.scheduling` backend module, plus one new Super Admin endpoint and a scheduled job; small new frontend feature folder.

**Performance Goals**: A nightly batch over a small number of Schedules (v1 scale) — no pagination/streaming needed; same order-of-magnitude latency tolerance as prior features.

**Constraints**:
- Each Schedule's Session generation runs in its own transaction (`generateForSchedule`, default `@Transactional`, invoked from a non-transactional `generate()` orchestrator) so a conflict/failure processing one Schedule never rolls back another Schedule's already-committed Sessions in the same run (research.md).
- Duplicate-generation for a (Schedule, date) pair MUST be impossible at the database layer (`uq_session_schedule_date`), not merely by an application-level pre-check (Constitution IV) — the pre-check exists for the common case's efficiency/idempotency; the constraint is the actual guarantee.
- The manual-trigger endpoint MUST reuse the existing Super Admin auth chain unchanged — no new security matcher, no new credential mechanism.
- This feature MUST create no table, entity, or row other than `Session` (FR-009) — no Slot logic of any kind.

**Scale/Scope**: Single feature — 1 new entity (`Session`), 1 migration, 1 repository, 1 service, 1 controller, 1 scheduled-trigger component, 1 new endpoint, 1 small frontend feature.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Test-First Development | PASS | Tests for every FR (first-generation, no-duplicate-on-repeat, per-schedule independence, mode-appropriate snapshotting, Super-Admin-only manual trigger) written before implementation. |
| II. Simplicity & YAGNI | PASS | No Slot entity/logic invented ahead of 012/013 (spec Scope Decisions); no SAVEPOINT-based row-level race recovery built for a race this small-scale system realistically never hits — per-schedule transaction isolation plus the DB constraint is the proportionate design (research.md). |
| III. Modular, Library-First Architecture | PASS | Stays inside `com.cms.scheduling` (Constitution's own named module); reads `Schedule` directly (same module), reuses the existing admin security chain rather than inventing a new authorization mechanism. |
| IV. Data Privacy & Integrity by Design | PASS | The `uq_session_schedule_date` DB constraint is exactly Principle IV's "close duplicate-creation races at the data layer" requirement, applied to a new concurrency-sensitive create operation. |

No violations — Complexity Tracking is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/015-nightly-session-generation/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── session-generation.md
└── tasks.md
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/cms/scheduling/
│   ├── Session.java                              # new entity
│   ├── SessionRepository.java                    # new
│   ├── SessionGenerationService.java              # new
│   ├── SessionGenerationController.java           # new — POST /api/v1/admin/sessions/generate
│   ├── NightlySessionGenerationTrigger.java        # new — @Scheduled cron
│   └── SessionGenerationSchedulingConfig.java      # new — @EnableScheduling
├── src/main/resources/db/migration/
│   └── V8__create_session.sql                     # new
└── src/test/java/com/cms/scheduling/integration/
    ├── AbstractSessionGenerationIntegrationTest.java
    ├── SessionGenerationFirstRunTest.java
    ├── SessionGenerationNoDuplicateOnRepeatTest.java
    ├── SessionGenerationPerScheduleIndependenceTest.java
    ├── SessionGenerationModeSnapshotTest.java
    └── SessionGenerationManualTriggerAuthTest.java

frontend/
├── src/features/session-generation/
│   ├── TriggerSessionGeneration.tsx    # new — Super Admin manual-trigger button
│   └── api.ts                          # new — fetch client, AdminCredentials/Basic-Auth pattern per clinic-verification/api.ts
└── tests/session-generation/
    └── TriggerSessionGeneration.test.tsx  # new
```

**Structure Decision**: Extends the existing `com.cms.scheduling` module (009/010) — no new backend module. A new `frontend/src/features/session-generation/` folder follows the one-folder-per-feature convention, reusing `PendingClinicsList`'s established `AdminCredentials`/Basic-Auth/sessionStorage pattern rather than a shared module (matching this codebase's existing per-feature duplication of that pattern).

## Post-Design Constitution Re-Check

Re-evaluated after Phase 1 (data model + contracts + quickstart, below): all four principles still PASS. The per-schedule-transaction + DB-constraint design (data-model.md) confirms Principle IV's data-layer-closure requirement holds in the detailed design.

## Complexity Tracking

*No violations — table intentionally left empty.*
