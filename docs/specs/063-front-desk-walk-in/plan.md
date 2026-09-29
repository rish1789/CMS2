# Implementation Plan: Front-Desk Walk-In Registration with a Walk-In Line

**Branch**: `063-front-desk-walk-in` | **Date**: 2026-09-24 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/063-front-desk-walk-in/spec.md`

## Summary

A new front-desk Walk-in screen registers a walk-in in one flow: patient → visit reason → one of today's doctor sessions → confirm.
- **Fixed-Time sessions**: the walk-in joins an untimed walk-in line (W1, W2…) built from the existing token-slot mechanism. Staff send walk-ins in with the existing Appeared action, which now records the time; the existing Complete action records the finish time.
- **Queue sessions**: a walk-in takes the next token, marked as a walk-in.
- **Retired**: the old walk-in slot insertion (025/058).

The central risk is putting untimed slots into Fixed-Time sessions. Research.md Decision 3 audits every place that assumes a Fixed-Time slot has a time, and guards each one.

## Technical Context

**Language/Version**: Java 21 (backend); TypeScript + React (frontend)

**Primary Dependencies**: Spring Boot (Web MVC, Data JPA, Security); Vite, Tailwind v4, Vitest

**Storage**: PostgreSQL. One migration `V39__front_desk_walk_in.sql`: booking visit reason + detail (with an OTHER-needs-detail check constraint), patient email, slot `appeared_at`/`completed_at`.

**Testing**: JUnit 5 + Mockito (unit), `@WebMvcTest` (contract), Testcontainers (integration, Docker-gated here); Vitest + Testing Library

**Target Platform**: Web application

**Project Type**: web-service + SPA (existing `backend/` + `frontend/`)

**Performance Goals**: The doctor step loads today's sessions in one existing call, plus one grouped count query per page. Live status polls at the existing ~20 s cadence per visible session.

**Constraints**:
- No second queue system.
- No new doctor-status logic.
- Booked appointment behavior unchanged.
- Tenant-scoped reads and writes.

**Scale/Scope**:
- Backend: 1 new service + controller; ~9 targeted guards; 3 DTO extensions; retire 1 service + controller + DTO.
- Frontend: 1 new page (stepper + walk-in line panel); Day Sheet walk-in section; nav and route changes; retire 1 form.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | How |
|---|---|---|
| I. Test-First | PASS (planned) | Every task pairs a failing test ahead of code. The migration's OTHER-needs-detail invariant gets an integration test proving it holds and that a violating row is rejected. |
| II. Simplicity & YAGNI | PASS | No new queue/status entities; reuses the token slot, the Appeared/Complete actions, the session list, live status and readiness. The retired walk-in code is removed, not left dead. |
| III. Modular, event-driven | PASS | The new service lives in `booking` and composes existing `scheduling`/`booking`/`inbox` collaborators, as `WalkInInsertionService` already did (a direct inbox call, per 038's precedent). No new cross-module reach-through. |
| IV. Privacy & Integrity | PASS | Email is optional contact data on the clinic patient record, covered by the existing 033 anonymization path. The plan adds `email` to the fields anonymization clears (task). No clinical record is touched. |
| Multi-tenancy | PASS | Session, patient and appointment type are all resolved scoped to `clinicId`, as the existing services do; the 062 access gate still applies. |
| Rationale note (scheduling/booking/waitlist changes) | PASS | Implements spec 063 by explicit product decision, **overriding backlog 025 and 058's walk-in insertion rules** (spec "Product Decisions" §5). Every other business rule (no-show sweep, auto-completion, cancellation, waitlist bump) is kept for timed slots and explicitly excludes untimed walk-in slots. |

**Post-design re-check**: PASS. The design adds nullable columns and one guarded constraint, and no new abstraction beyond the one registration service.

## Project Structure

### Documentation (this feature)

```text
specs/063-front-desk-walk-in/
├── plan.md, research.md, data-model.md, quickstart.md
├── contracts/front-desk-walk-in.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/resources/db/migration/V39__front_desk_walk_in.sql            # NEW
backend/src/main/java/com/cms/
├── booking/domain/{Booking.java (+visitReason), VisitReason.java (NEW)}
├── booking/service/FrontDeskWalkInService.java                                   # NEW
├── booking/api/FrontDeskWalkInController.java                                    # NEW
├── booking/dto/{FrontDeskWalkInRequest,FrontDeskWalkInResponse}.java             # NEW
├── booking/exception/{VisitReason*,DuplicateWalkIn,InvalidEmail,PatientRequired,WalkInNotSelfCancellable}Exception.java  # NEW
├── booking/exception/BookingExceptionHandler.java                                # map new errors
├── booking/service/{BookingCancellationService,StaffBookingService,PatientBookingService,SessionPartialCancellationService,QueuePositionService}.java  # guards
├── booking/api/PatientBookingCancellationController.java                         # walk-in refusal
├── booking/dto/SessionDaySheetResponse.java                                      # +times, +visit reason
├── booking/service/WalkInInsertionService.java, booking/api/WalkInInsertionController.java, booking/dto/WalkInRequest.java  # REMOVED
├── scheduling/domain/Slot.java (+appearedAt, completedAt, isUntimed())
├── scheduling/service/{QueueSlotService (issueNextWalkInSlot), SlotAppearedService, SlotCompletionService}.java
├── scheduling/repository/SlotRepository.java                                     # untimed filters + walk-in counts
├── scheduling/dto/SessionSummaryResponse.java, scheduling/api/ClinicSessionListController.java  # walkInsWaiting, inWithDoctor
└── patient/record/{domain/Patient.java (+email), service/PatientAnonymizationService.java (clear email)}

frontend/src/
├── features/front-desk-walk-in/{FrontDeskWalkInPage,PatientStep,VisitReasonStep,SessionStep,WalkInLinePanel,api,visitReasons}.tsx|ts  # NEW
├── features/day-sheet/{SessionSlotsView.tsx, api.ts}                              # walk-in section, new fields, button links
├── features/staff-booking/WalkInForm.tsx (+test)                                 # REMOVED
├── routes/staff/{ClinicToolPages.tsx, StaffShell.tsx}, App.tsx                   # route, redirect, nav
└── features/booking-cancellation/api.ts                                          # WALK_IN_NOT_SELF_CANCELLABLE message
```

**Structure Decision**: Existing web-application layout; each change lands in the module that owns the behavior.

## Complexity Tracking

| Addition | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| Untimed slots inside Fixed-Time sessions (plus ~9 guards) | The product decision is a walk-in line that takes no timed slot, reusing the existing token mechanism. | A separate walk-in table/queue is a second queue system (explicitly forbidden). Keeping walk-ins in timed slots contradicts the product decision. |
