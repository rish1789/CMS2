# Implementation Plan: Session Delay Tracking (Fixed-Time Only)

**Branch**: `026-session-delay-tracking` | **Date**: 2026-09-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/026-session-delay-tracking/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Give staff a way to mark a booked Fixed-Time Slot completed (a genuinely new capability — no
prior feature built any completion action), and recalculate-and-store a per-Session delay figure
at exactly two trigger points: that completion action, and a walk-in insertion (025). A separate
read-only endpoint returns the last-stored figure without ever recomputing it live. Technical
approach: two new services in the existing `com.cms.scheduling` module (`SlotCompletionService`,
`SessionDelayService`), one new `SlotStatus` value, one new nullable `Session.delayMinutes`
column, and one new call site added to 025's already-converged `WalkInInsertionService`
(research.md).

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript/React (frontend) — matches every prior feature this session.

**Primary Dependencies**: Spring Boot, Spring Data JPA, Spring Security (staff JWT chain); React + Tailwind CSS on the frontend.

**Storage**: PostgreSQL via Flyway-versioned migrations.

**Testing**: JUnit 5 + Spring Boot Test (`@WebMvcTest`/`@SpringBootTest` + Testcontainers) on the backend; Vitest/React Testing Library on the frontend — mirrors every prior feature.

**Target Platform**: Linux server (Spring Boot backend), browser (React frontend).

**Project Type**: Web application (`backend/` + `frontend/`, existing structure).

**Performance Goals**: No new performance target — a single Session's Slot list (bounded by one clinic-day's schedule) evaluated in-memory, reusing the existing indexed `findBySession_Id` query.

**Constraints**: Delay reads must never trigger recomputation (FR-005) — reads and the two write/recalculate call sites are structurally separate methods.

**Scale/Scope**: Two new service classes, two new controllers, one enum value, one nullable column migration, one new call site added to an already-converged feature (025), one new frontend affordance.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Test-First Development**: PASS. Tasks will include failing tests (unit + Testcontainers
  integration, mirroring 021/022/025's precedent) for the completion action's status gate, the
  delay formula, both trigger call sites, and the Queue-mode exclusion, written before the
  corresponding implementation.
- **II. Simplicity & YAGNI**: PASS. No new entity/table for the delay figure (a nullable scalar
  column, R2); no event-based decoupling where a plain synchronous call suffices (R5); the
  completion action's authorization check is deliberately duplicated, not generalized with
  `ScheduleService`'s differently-scoped existing helper (R7) — each is a direct application of
  this codebase's own established precedent, not new complexity.
- **III. Modular, Library-First Architecture**: PASS. Lives entirely inside the existing
  `com.cms.scheduling` module, exposes two clear REST contracts, and reaches into
  `com.cms.booking`'s already-converged `WalkInInsertionService` only as a new caller of
  `com.cms.scheduling`'s own service (the established one-directional dependency, unchanged) —
  never the reverse.
- **IV. Data Privacy & Integrity by Design**: PASS. No patient-identifying data touched. The one
  integrity-sensitive design point — reads never recomputing, writes only at the two defined
  trigger points — is structurally guaranteed by keeping `recalculate` and `currentDelay` as
  separate methods with no shared code path (data-model.md).

No violations — Complexity Tracking table not needed.

**Post-Phase-1 re-check**: PASS, unchanged. Phase 1 design (data-model.md, contracts/,
quickstart.md) introduced nothing beyond what this gate already evaluated.

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/cms/scheduling/
│   ├── Slot.java                        # unchanged (setStatus already exists)
│   ├── SlotStatus.java                  # extend: + COMPLETED
│   ├── Session.java                     # extend: + delayMinutes field
│   ├── SlotCompletionService.java       # new
│   ├── SlotCompletionController.java    # new
│   ├── SlotNotFoundException.java       # new (scheduling-side; booking's own is a different class)
│   ├── SlotNotCompletableException.java # new
│   ├── SessionDelayService.java         # new
│   ├── SessionDelayController.java      # new
│   └── ScheduleExceptionHandler.java    # extend: + 2 new mappings
├── src/main/java/com/cms/booking/
│   └── WalkInInsertionService.java      # extend: + 1 new call site (025, already converged)
├── src/main/resources/db/migration/
│   └── V15__session_delay_minutes.sql   # new
└── src/test/java/com/cms/scheduling/
    └── integration/                      # new package: completion + delay integration tests

frontend/
├── src/features/session-delay/
│   ├── api.ts                           # new
│   ├── CompleteSlotButton.tsx           # new
│   └── DelayIndicator.tsx               # new
└── src/features/session-delay/__tests__/  # actual path confirmed at Tasks time against this
                                            # project's real test-directory convention (025 found
                                            # tasks.md's assumed __tests__ path was wrong — verify
                                            # frontend/tests/<feature>/ instead before writing tasks)
```

**Structure Decision**: Existing web-application structure (`backend/` + `frontend/`). Backend
work lives entirely in the existing `com.cms.scheduling` module (011/012/021/022/025's home) plus
one new call site in `com.cms.booking`'s already-converged `WalkInInsertionService`; frontend work
is a new `session-delay` feature directory, following `staff-booking`'s established shape.

## Complexity Tracking

*No violations — this section is not applicable.*
