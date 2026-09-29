# Implementation Plan: Doctor Live Schedule Status

**Branch**: `061-doctor-live-status` | **Date**: 2026-09-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/061-doctor-live-status/spec.md`

## Summary

Extend the existing Session Delay Tracking feature with an on-demand, continuously-polled live schedule status (`NOT_STARTED`/`ON_TIME`/`RUNNING_EARLY`/`DELAYED`/`COMPLETED`) for Fixed-Time sessions, computed by comparing an **actual progression pointer** (earliest participating slot not yet resolved) against an **expected progression pointer** (the participating slot whose scheduled window contains "now") — both derived purely from existing `Slot.status` + `Slot.startTime`, requiring no schema change. Adds a new doctor/staff read endpoint and a new patient read endpoint, both reusing existing scoping patterns; adds one centralized operational-day (04:30 AM boundary) function; extends the existing `SessionOperationsPanel` and adds a new patient-facing indicator, both polling every ~20s via the same client-side pattern `QueuePositionIndicator` already established. The existing trigger-based `SessionDelayService.recalculate`/`Session.delayMinutes` mechanism is left untouched for any other consumer.

## Technical Context

**Language/Version**: Java 21 (backend, Spring Boot 3.3), TypeScript 5 / React 19 (frontend, Vite)

**Primary Dependencies**: Spring Data JPA/Hibernate — no new dependency of any kind

**Storage**: PostgreSQL — no new migration. No new column, table, or index (spec Assumptions A1/A2) — every new query reads existing `Session`/`Slot` columns through existing/derived-query repository methods.

**Testing**: JUnit 5 + Mockito (unit), `@WebMvcTest` (contract), Testcontainers (integration, written/compiled but unexecuted in this sandbox per the project's standing Docker limitation) on the backend; Vitest + Testing Library on the frontend

**Target Platform**: Existing web app (Spring Boot backend on :8080, Vite/React frontend on :5173) — no new platform

**Project Type**: Web application (existing `backend/` + `frontend/` structure)

**Performance Goals**: Both new reads must be cheap enough to poll every ~20s per open session/booking without added load beyond what `QueuePositionIndicator` already contributes at the same cadence — a single-session slot-list read plus in-memory pointer comparison, no aggregation query, no N+1.

**Constraints**: Read-only — no new write path anywhere in this feature. No new module. Must not create a `booking ↔ scheduling` cycle (the one new cross-module call, `booking`'s patient endpoint → `scheduling`'s live-status computation, follows the same one-way direction `Booking.slot` already establishes). Must not alter Queue-mode behavior (024) or the existing `/delay` endpoint's contract.

**Scale/Scope**: Touches `com.cms.scheduling` (new `OperationalDayService`, new `SessionLiveStatusService`, one new method on `SessionDelayController`) and `com.cms.booking` (one new controller mirroring `PatientQueuePositionController`), plus the frontend's `session-delay` feature area (one new component + two new API client calls) and the patient booking detail page (one new indicator wired in). No new module, no schema change.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Test-First Development**: New `SessionLiveStatusService`/`OperationalDayService` logic gets unit coverage before/with implementation (pure calculation, no Spring context needed — mirrors `SessionDelayServiceTest`'s existing shape); new endpoints get contract coverage for success + every failure mode (401/404/Queue-mode `applicable:false`); integration coverage proves the full lifecycle (Not started → Delayed → catches up → Running early) against a real database, per spec's own Testing Strategy. **PASS**.
- **II. Simplicity & YAGNI**: No new DTOs beyond what's needed to carry the two new read shapes (no generic "status framework"). No new persisted data (A1/A2) — this is the single biggest simplicity win, confirmed during specification, not assumed. A separate `SessionLiveStatusService` (rather than bolting live computation onto `SessionDelayService`) is justified because the two computations have genuinely different shapes (cached-and-trigger-recalculated vs. pure-on-demand-read) — mirrors this codebase's own existing precedent of one service per distinct behavior (`SlotAppearedService`/`SlotCompletionService`/`SlotAutoCompletionService` are already split this way despite all touching `Slot.status`). **PASS**.
- **III. Modular, Library-First Architecture**: All new backend code stays inside `com.cms.scheduling` (the module that already owns `Session`/`Slot`/`SessionDelayService`) except the one new patient controller, which lives in `com.cms.booking` (the module that already owns `Booking` and already depends one-way on `scheduling` via `Booking.slot`) — calling into a new `scheduling` service method is additive to an already-established dependency direction, not a new or reverse one. **PASS**.
- **IV. Data Privacy & Integrity by Design**: No new patient-identifying field anywhere; every current-patient reference is an ordinal position (FR-004/FR-011), never a name. No new write path, so no new concurrency/race surface. Consultation Notes/Prescriptions immutability is untouched — this feature never reads or writes clinical documentation. **PASS**.
- **Multi-tenancy**: The new staff/doctor endpoint reuses `SessionDelayController`'s existing clinic-scoping + doctor-self-scoping fail-closed pattern exactly (FR-012). The new patient endpoint reuses `PatientQueuePositionController`'s existing booking-ownership-check pattern exactly (FR-013). No new authorization approach is invented. **PASS**.
- **Out-of-scope boundaries**: No payments, uploads, notifications, or reschedule touched. Queue-mode sessions remain entirely outside this feature's calculation (FR-003) — queue position (024) is untouched. **PASS**.

No violations to justify — Complexity Tracking table is intentionally omitted below.

**Post-Design Re-check** (after Phase 0/1 artifacts below): still **PASS** on every principle. The one design decision most worth re-confirming — a new `SessionLiveStatusService` rather than extending `SessionDelayService` in place — was reinforced, not weakened, once the contract shapes were written out in Phase 1: keeping the two services separate is what lets the existing `SessionDelayServiceTest`/`SessionDelayAuthorizationTest`/`SessionDelayQueueModeTest`/`SessionDelayReadOnlyTest`/`SessionDelayNoOutstandingDelayTest` suite stay provably untouched (spec's own regression-check requirement), rather than risking a shared-class refactor accidentally changing the cached-recalculation behavior those tests pin down.

## Project Structure

### Documentation (this feature)

```text
specs/061-doctor-live-status/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit-tasks — not created here)
```

### Source Code (repository root)

```text
backend/src/main/java/com/cms/
├── scheduling/
│   ├── service/
│   │   ├── OperationalDayService.java              # NEW — single 04:30 boundary function (BR-011/BR-012)
│   │   ├── SessionLiveStatusService.java            # NEW — BR-005–BR-010 pointer calculation, on-demand
│   │   └── SessionDelayService.java                 # existing — untouched (A6)
│   ├── dto/
│   │   └── SessionLiveStatusResponse.java            # NEW — staff/doctor response shape
│   └── api/
│       └── SessionDelayController.java                # + one new GET method (live-status), existing /delay method untouched
└── booking/
    ├── service/ (no change — read-only call into scheduling)
    ├── dto/
    │   └── PatientSessionLiveStatusResponse.java       # NEW — patient-safe response shape
    └── api/
        └── PatientSessionLiveStatusController.java     # NEW — mirrors PatientQueuePositionController exactly

frontend/src/
├── features/
│   └── session-delay/
│       ├── api.ts                                     # + 2 new client calls (staff + patient live-status)
│       ├── LiveScheduleStatusIndicator.tsx             # NEW — mirrors QueuePositionIndicator's {mode:'staff'|'patient'} + polling shape
│       ├── DelayIndicator.tsx                           # existing — left in place (A6); may be superseded in SessionOperationsPanel by the new indicator at implementation time
│       └── SessionOperationsPanel.tsx                   # + Current/Expected Patient, First Slot, Operational Day fields (FR-008)
└── routes/patient/
    └── PatientPages.tsx                               # PatientBookingDetailPage — + LiveScheduleStatusIndicator alongside existing QueuePositionIndicator/CancelBookingButton/VisitRecordSection
```

**Structure Decision**: Existing `backend/` + `frontend/` web-application layout, unchanged. All new backend logic stays inside the two modules that already own the relevant entities (`scheduling` for the calculation, `booking` for the patient-facing read) — no new module, no schema change. Frontend additions stay inside the existing `session-delay` feature directory and the existing patient booking detail page.

## Complexity Tracking

*No Constitution Check violations — table intentionally omitted.*
