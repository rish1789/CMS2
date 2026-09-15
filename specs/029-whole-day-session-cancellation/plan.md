# Implementation Plan: Whole-Day Session Cancellation

**Branch**: `029-whole-day-session-cancellation` | **Date**: 2026-09-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/029-whole-day-session-cancellation/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Let staff cancel an entire Session (Fixed-Time or Queue-mode) in one action — every currently-
active Booking within it is cancelled and its Slot released back to `OPEN`, exactly mirroring
025's per-Booking transition, but explicitly never publishing 025's waitlist-bump event (the sole,
deliberately narrow trigger for that remains individual voluntary cancellation only). This is
also the first real production caller of 036's `NotificationEventService.publish`. Technical
approach: one new `SessionCancellationService` in `com.cms.booking` that reuses 028's
`cancelIfActive` data-layer guard directly (not 028's own `BookingCancellationService.cancel`,
since that always publishes the event this feature must never fire) — no new entity, no new enum
value, no migration (research.md).

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript/React (frontend) — matches every prior feature this session.

**Primary Dependencies**: Spring Boot, Spring Data JPA, Spring Security (staff JWT chain); React + Tailwind CSS on the frontend.

**Storage**: PostgreSQL — no schema change this feature (reuses 028's existing columns/constraint entirely).

**Testing**: JUnit 5 + Spring Boot Test (`@WebMvcTest`/`@SpringBootTest` + Testcontainers) on the backend; Vitest/React Testing Library on the frontend — mirrors every prior feature.

**Target Platform**: Linux server (Spring Boot backend), browser (React frontend).

**Project Type**: Web application (`backend/` + `frontend/`, existing structure).

**Performance Goals**: No new performance target — one Session's Slot list (bounded by one clinic-day's schedule), each Booking cancelled via its own already-proven-fast conditional update.

**Constraints**: Must never publish `BookingCancelledEvent` under any circumstance (FR-003) — structurally guaranteed by not calling 028's `BookingCancellationService.cancel` at all, not by a conditional inside it.

**Scale/Scope**: One new service, one new controller, one new exception, one new frontend component. No new entity, no migration, no changes to any already-converged feature's code (unlike 028, which had to touch `WalkInInsertionService`).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Test-First Development**: PASS. Tasks will include failing tests (unit + Testcontainers
  integration) for the bulk-cancel success path (both Session modes), the already-resolved-Slot
  exclusion, the "nothing to cancel" rejection, the no-event-ever guarantee, the notification
  skip for walk-ins, and a concurrency test proving this action and an individual 025
  cancellation targeting the same Slot never both "win."
- **II. Simplicity & YAGNI**: PASS. No new entity, enum value, or migration; no Session-level
  "cancelled" flag (Clarifications explicitly rejected that path); reuses 028's exact
  `cancelIfActive` primitive rather than inventing a bulk-specific variant.
- **III. Modular, Library-First Architecture**: PASS. Lives entirely inside `com.cms.booking`;
  becomes the first real caller of `com.cms.notification`'s existing service-only contract
  (`NotificationEventService.publish`), exercising the event-driven cross-module interface
  Constitution III already names as this codebase's own pattern, not a new one.
- **IV. Data Privacy & Integrity by Design**: PASS. Each Booking's cancellation is individually
  guarded at the data layer (028's `cancelIfActive`), not a single unguarded bulk statement —
  concurrent per-Booking actions from other features remain correctly race-safe (research.md R4).

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
├── src/main/java/com/cms/booking/
│   ├── SessionCancellationService.java        # new
│   ├── SessionCancellationController.java     # new
│   ├── SessionAlreadyCancelledException.java  # new
│   ├── dto/SessionCancellationResponse.java   # new
│   └── BookingExceptionHandler.java           # extend: + 1 new mapping
└── src/test/java/com/cms/booking/integration/
    └── (new test classes)

frontend/
├── src/features/session-cancellation/
│   ├── api.ts                                 # new
│   └── CancelSessionButton.tsx                # new
└── tests/session-cancellation/                # new
```

**Structure Decision**: Existing web-application structure (`backend/` + `frontend/`). Backend
work lives entirely in the existing `com.cms.booking` module (015/016/017/018/025/028's home) —
no changes needed to any other module's `SecurityConfig` (staff-only, clinic-scoped, the same
`/api/v1/clinics/**` chain 025/026/027/028 already extend); frontend work is a new
`session-cancellation` feature directory.

## Complexity Tracking

*No violations — this section is not applicable.*
