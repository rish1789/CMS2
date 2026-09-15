# Implementation Plan: Partial (Cutoff-Based) Session Cancellation

**Branch**: `030-partial-cutoff-session-cancellation` | **Date**: 2026-09-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/030-partial-cutoff-session-cancellation/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Let staff cancel only the trailing portion of a Session from a chosen cutoff time onward — every
`BOOKED` Slot at or after that time has its Booking cancelled and Slot released, exactly like
029's whole-session action, just filtered by time. Never triggers a waitlist bump (same exclusion
as 029); a cutoff matching nothing is a normal zero-count success, not an error (the one
deliberate behavioral difference from 029). Technical approach: `SessionPartialCancellationService`
mirrors 029's `SessionCancellationService` almost exactly, adding one per-mode time filter
(Fixed-Time via `Slot.startTime`, Queue-mode via `Slot.createdAt` — Clarifications) — no new
entity, enum value, or migration (research.md).

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript/React (frontend) — matches every prior feature this session.

**Primary Dependencies**: Spring Boot, Spring Data JPA, Spring Security (staff JWT chain); React + Tailwind CSS on the frontend.

**Storage**: PostgreSQL — no schema change (reuses 025/026's existing columns/constraint entirely).

**Testing**: JUnit 5 + Spring Boot Test (`@WebMvcTest`/`@SpringBootTest` + Testcontainers) on the backend; Vitest/React Testing Library on the frontend — mirrors every prior feature.

**Target Platform**: Linux server (Spring Boot backend), browser (React frontend).

**Project Type**: Web application (`backend/` + `frontend/`, existing structure).

**Performance Goals**: No new performance target — identical scale to 029.

**Constraints**: Must never publish `BookingCancelledEvent` (FR-005) — structurally guaranteed the same way 029 already proved (never calling `BookingCancellationService.cancel`).

**Scale/Scope**: One new service, one new controller, one new request DTO (reuses 029's response DTO), one new frontend component. No new entity, no migration.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Test-First Development**: PASS. Tasks will include failing tests for the cutoff filter
  (both Session modes), the before-cutoff/`COMPLETED`/`OPEN`-untouched exclusions, the
  zero-qualifying-is-not-an-error behavior (the one real divergence from 029, deserving its own
  explicit test rather than assuming 029's coverage extends to it), the no-waitlist-bump
  guarantee, and a concurrency test mirroring 029's cross-feature race proof.
- **II. Simplicity & YAGNI**: PASS. No new entity, enum value, or migration; reuses 029's response
  DTO rather than duplicating an identical shape (research.md R4); the Queue-mode time proxy
  (`createdAt`) uses a field that already exists rather than adding a new one.
- **III. Modular, Library-First Architecture**: PASS. Lives entirely inside `com.cms.booking`,
  reuses `NotificationEventService`'s existing service-only contract exactly as 029 already
  established as this codebase's pattern for it.
- **IV. Data Privacy & Integrity by Design**: PASS. Same per-Slot data-layer race-closure as 029
  (`cancelIfActive`), unchanged.

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
│   ├── SessionPartialCancellationService.java     # new
│   ├── SessionPartialCancellationController.java  # new
│   └── dto/PartialCancellationRequest.java        # new (reuses 029's SessionCancellationResponse)
└── src/test/java/com/cms/booking/integration/
    └── (new test classes)

frontend/
├── src/features/partial-session-cancellation/
│   ├── api.ts                                     # new
│   └── CancelFromCutoffForm.tsx                   # new
└── tests/partial-session-cancellation/            # new
```

**Structure Decision**: Existing web-application structure (`backend/` + `frontend/`). Backend
work lives entirely in the existing `com.cms.booking` module — no `SecurityConfig` chain changes
beyond one new matcher (reusing the same `/api/v1/clinics/**` chain 025/026/027/028/029 already
extend); frontend work is a new `partial-session-cancellation` feature directory.

## Complexity Tracking

*No violations — this section is not applicable.*
