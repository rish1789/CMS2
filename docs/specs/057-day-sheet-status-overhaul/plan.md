# Implementation Plan: Day Sheet Smart Status Flow

**Branch**: `057-day-sheet-status-overhaul` | **Date**: 2026-09-21 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/057-day-sheet-status-overhaul/spec.md`

## Summary

Add a new `APPEARED` slot status between `BOOKED` and `COMPLETED` for Fixed-Time slots, so a slot auto-completes once its scheduled time passes instead of requiring a manual click; keep the existing automatic No-Show sweep's timing unchanged but make it (and its outcome) reversible by an explicit "mark Appeared" action; extend slot-completion authorization to the treating doctor while keeping "Appeared"/No-Show/cancellation ClinicAdmin-and-Operations-only in the UI; and replace the Day Sheet's per-row inline Cancel link with a checkbox multi-select that reuses today's existing single-booking cancellation logic per selected slot (never a new, simplified cancellation path).

## Technical Context

**Language/Version**: Java 21 (backend, Spring Boot 3.3), TypeScript 5 / React 19 (frontend, Vite)

**Primary Dependencies**: Spring Data JPA/Hibernate, Spring `@Scheduled` (existing cron-sweep pattern), React Router 6 (`Outlet` context), Tailwind CSS — no new dependency of any kind (research.md confirms nothing here requires one)

**Storage**: PostgreSQL — no new migration. `slot.status` is a plain `VARCHAR(20)` with no check constraint (confirmed against `V10__create_slot.sql`/`V13__slot_no_show_and_hold_support.sql`'s own precedent: a new `SlotStatus` enum constant is "persisted as a string - it needs no schema change of its own"), so adding `APPEARED` to the Java enum is the entire storage change.

**Testing**: JUnit 5 + Mockito (unit), `@WebMvcTest` (contract), Testcontainers (integration, written/compiled but unexecuted in this sandbox per the project's standing Docker limitation) on the backend; Vitest + Testing Library on the frontend

**Target Platform**: Existing web app (Spring Boot backend on :8080, Vite/React frontend on :5173) — no new platform

**Project Type**: Web application (existing `backend/` + `frontend/` structure)

**Performance Goals**: No new goals beyond the existing No-Show sweep's own precedent (per-minute cron sweep over a small "currently eligible" candidate set, not a full-table scan)

**Constraints**: Fixed-Time slots only (FR-013); zero change to Queue/Token-mode behavior; zero change to consultation notes/prescriptions/clinical documentation; existing single-slot cancellation's business rules (waitlist trigger, etc.) MUST be reused unmodified per selected slot, never bypassed (FR-012)

**Scale/Scope**: Touches the `scheduling` module (slot status + two new sweep components), the `booking` module (cancellation eligibility + new batch-cancel endpoint), and the frontend Day Sheet feature area plus the shared `ClinicShell`/role-plumbing. No new module.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Test-First Development**: Every new backend behavior below (status transition, two new sweeps, extended authorization, extended cancellation eligibility, batch-cancel) gets unit coverage before/with implementation, mirroring this codebase's own established sweep-service test pattern (`NoShowDetectionService`'s tests). Integration tests are written and compiled per this project's standing convention even though they can't execute in this sandbox. **PASS** (no violation — enforced at task-generation/implementation time, not a plan-time gate failure).
- **II. Simplicity & YAGNI**: No new module, no new abstraction layer, no configurability beyond what's asked. The two new sweeps are a direct structural copy of the one sweep pattern this codebase already has (`NoShowDetectionService`/`NoShowDetectionTrigger`), not a new generic "sweep framework." The batch-cancel endpoint reuses `BookingCancellationService.cancel(Booking)` unchanged, per booking, rather than inventing a new cancellation code path. **PASS**.
- **III. Modular, Library-First Architecture**: The doctor-completion-authorization check is added *inside* `scheduling` (which already owns `Slot`/`Session`/`DoctorProfile`), not by reaching into the `clinical` module's `TreatingDoctorAuthorizationService` — reusing that service would create a `scheduling → clinical → booking → scheduling` cycle across module boundaries. See research.md Decision 5. **PASS**.
- **IV. Data Privacy & Integrity by Design**: No new patient-identifying data is introduced (a slot's status is not clinical documentation and carries no new PII). Concurrency: the auto-completion sweep follows the exact non-`@Transactional`-outer, per-candidate-save pattern the No-Show sweep already uses specifically to avoid the two self-invocation transaction bugs this codebase has hit before (research.md Decision 2); the batch-cancel endpoint reuses `BookingCancellationService`'s existing `REQUIRES_NEW`-per-booking design, which was already built for exactly this batch scenario. **PASS**.
- **Multi-tenancy**: Every new/changed endpoint stays clinic-scoped through the same `clinicId` path-and-authorization pattern every existing sibling endpoint in these two modules already uses (`RoleAssignmentRepository...Clinic_Id...`). **PASS**.
- **Out-of-scope boundaries**: No payments, no file uploads, no notification delivery, no atomic reschedule are touched. **PASS**.

No violations to justify — Complexity Tracking table is intentionally omitted below.

**Post-Design Re-check** (after Phase 0/1 artifacts below): still **PASS** on every principle. The one design refinement made during Phase 1 — keeping ClinicAdmin/Operations' existing `BOOKED→COMPLETED` path unchanged alongside the new `APPEARED`-gated paths, rather than forcing every completion through Appeared — reinforces Principle II/Governance's "additive, not silently narrowed" stance rather than introducing new complexity; it added a documented row to the state-transition table (data-model.md), not a new abstraction.

## Project Structure

### Documentation (this feature)

```text
specs/057-day-sheet-status-overhaul/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md         # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit-tasks — not created here)
```

### Source Code (repository root)

```text
backend/src/main/java/com/cms/
├── scheduling/
│   ├── domain/
│   │   └── SlotStatus.java                       # + APPEARED value
│   ├── service/
│   │   ├── SlotCompletionService.java             # extend requireAuthorized (+ treating doctor); add completeSlotAutomatically(Slot)
│   │   ├── SlotAppearedService.java               # NEW — BOOKED|NO_SHOW -> APPEARED (manual)
│   │   ├── SlotAutoCompletionService.java         # NEW — sweep: APPEARED + end-time-passed -> COMPLETED
│   │   ├── SlotAutoCompletionTrigger.java         # NEW — @Scheduled, mirrors NoShowDetectionTrigger
│   │   └── NoShowDetectionService.java            # unchanged (candidate query already excludes non-BOOKED)
│   ├── repository/
│   │   └── SlotRepository.java                    # + findAppearedFixedTimeCandidatesForAutoCompletion()
│   └── api/
│       └── SlotAppearedController.java            # NEW — POST .../slots/{slotId}/appeared
└── booking/
    ├── service/
    │   └── BookingCancellationService.java         # extend eligible-status guard: BOOKED or APPEARED
    └── api/
        └── BatchBookingCancellationController.java # NEW — POST .../sessions/{sessionId}/bookings/cancel-batch

frontend/src/
├── routes/staff/
│   └── ClinicShell.tsx                             # pass `role` via <Outlet context>
├── features/day-sheet/
│   └── SessionSlotsView.tsx                        # role-conditional actions; checkbox selection; remove inline Cancel
├── features/session-delay/
│   ├── CompleteSlotButton.tsx                       # unchanged (doctor now simply passes the existing backend check)
│   └── AppearedButton.tsx                          # NEW
└── features/booking-cancellation/
    └── BatchCancelBar.tsx                          # NEW — selection summary + confirm action
```

**Structure Decision**: Existing `backend/` + `frontend/` web-application layout, unchanged. New backend code lives in the two modules that already own the relevant domain objects (`scheduling` for slot status/sweeps, `booking` for cancellation) — no new module, per Constitution III and the Simplicity gate above.

## Complexity Tracking

*No Constitution Check violations — table intentionally omitted.*
