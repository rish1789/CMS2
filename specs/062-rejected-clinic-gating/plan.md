# Implementation Plan: Rejected Clinics Stop Operating

**Branch**: `062-rejected-clinic-gating` | **Date**: 2026-09-24 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/062-rejected-clinic-gating/spec.md`

## Summary

A rejected clinic stops taking appointments. Rejection:
- cancels its upcoming bookings with a patient-visible reason, without waitlist offers;
- closes its waitlist;
- stops its session generation;
- limits staff access to its ClinicAdmin.

Restore lifts every rule at once. The work is backend enforcement at existing choke points plus two small frontend messages. There are no new endpoints and no schema change (research.md).

## Technical Context

**Language/Version**: Java 21 (backend); TypeScript + React (frontend)

**Primary Dependencies**: Spring Boot (Web MVC, Security, Data JPA, transactional events); Vite, Tailwind v4, Vitest

**Storage**: PostgreSQL. **No migration** — the new `CLINIC_REJECTED` literal fits the unconstrained `booking.cancellation_reason VARCHAR(50)` (V31).

**Testing**: JUnit 5 + Mockito (unit), `@WebMvcTest` (contract), Testcontainers (integration, Docker-gated in this sandbox); Vitest + Testing Library (frontend)

**Target Platform**: Web application (Spring Boot API + React SPA)

**Project Type**: web-service + SPA (existing `backend/` + `frontend/`)

**Performance Goals**: The staff access gate adds one clinic primary-key read per clinic-scoped staff request, and a role lookup only when the clinic is rejected. The rejection cascade runs once per rejection, after commit.

**Constraints**:
- The existing tenant-scoping, immutability, and module-event boundaries stay intact.
- Existing `@WebMvcTest` slices must keep starting unchanged (research.md Decision 6).

**Scale/Scope**:
- 5 booking services, 1 generator, 1 reject action, 2 event listeners, 1 interceptor, 1 login check, 1 membership query.
- 3 frontend surfaces: My bookings, staff sign-in, booking-form errors.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | How |
|---|---|---|
| I. Test-First | PASS (planned) | Every task pairs a failing unit, contract, or integration test ahead of its code. Integration tests prove each business rule against a real database (Docker-gated here, per the standing sandbox limitation). |
| II. Simplicity & YAGNI | PASS, with one justified addition | No new endpoint, table, or migration. One new cross-cutting interceptor is justified in Complexity Tracking. |
| III. Modular, event-driven | PASS | identity.admin publishes `ClinicRejectedEvent`. booking and waitlist react through their own AFTER_COMMIT listeners, mirroring `ClinicDeVerifiedEvent` exactly. The booking check reads `Clinic` through the existing Session → Clinic association (booking already depends on identity). |
| IV. Privacy & Integrity | PASS | No clinical record is touched. Past and completed bookings are untouched. Cancellations use the existing race-safe `cancelIfActive`. Past-booking behavior under the 034 retention lifecycle is unchanged. |
| Multi-tenancy | PASS | Every check reads only the clinic the request or entity names (FR-008). The interceptor keys on the `{clinicId}` path variable. |
| Rationale note (scheduling/booking/cancellation/waitlist changes) | PASS | Implements spec 062 FR-001–FR-011. It adds to — never changes — 008's de-verification cascade, 011's generation, 016/017/018/020/029's booking paths, and 028/029's waitlist lifecycle. |

**Post-design re-check**: still PASS. Phase 1 introduced no new storage, endpoint, or dependency.

## Project Structure

### Documentation (this feature)

```text
specs/062-rejected-clinic-gating/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/rejected-clinic-gating.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/com/cms/
├── identity/admin/domain/ClinicRejectedEvent.java                         # NEW
├── identity/admin/service/ClinicVerificationService.java                  # publish on reject
├── identity/account/service/StaffAuthService.java                         # sign-in rule
├── identity/account/exception/StaffClinicNotActiveException.java          # NEW (403 CLINIC_NOT_ACTIVE)
├── identity/account/config/RejectedClinicAccessInterceptor.java           # NEW
├── identity/account/config/RejectedClinicAccessWebConfig.java             # NEW (ObjectProvider-registered)
├── identity/account/service/RejectedClinicAccessGate.java                 # NEW (the check itself)
├── identity/account/api/StaffClinicController.java                        # /mine filter
├── identity/account/repository/RoleAssignmentRepository.java              # new membership query
├── booking/domain/BookingCancellationReason.java                          # + CLINIC_REJECTED
├── booking/exception/ClinicNotAcceptingAppointmentsException.java         # NEW (409)
├── booking/exception/BookingExceptionHandler.java                         # map 409
├── booking/service/{Patient,PatientQueue,Staff,StaffQueue}BookingService.java, WalkInInsertionService.java  # check
├── booking/service/ClinicRejectionCascadeService.java + ...Listener.java  # NEW
├── booking/repository/BookingRepository.java                              # findActiveUpcomingBookingsByClinic
├── booking/api/PatientBookingCancellationController.java                  # refuse CLINIC_REJECTED input
├── booking/dto/PatientBookingSummaryResponse.java                         # + cancellationReason
├── waitlist/service/ClinicRejectionWaitlistListener.java                  # NEW
├── waitlist/repository/WaitlistEntryRepository.java                       # expireOpenByClinic
└── scheduling/service/ScheduleSessionGenerator.java                       # skip rejected

frontend/src/
├── features/patient-bookings/{api.ts,MyBookings.tsx}                      # reason message
├── features/staff-login/*                                                 # CLINIC_NOT_ACTIVE message
└── booking forms                                                          # surface 409 via existing error paths
```

**Structure Decision**: Existing web-application layout (`backend/` package-per-feature, `frontend/src/features`). Each change lands in the module that owns the behavior.

## Complexity Tracking

| Addition | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| `RejectedClinicAccessInterceptor` (new cross-cutting MVC interceptor) | FR-007 must hold on every clinic-scoped staff request, including tokens issued before rejection. | Editing each of the dozens of per-service "staffed at clinic" checks is error-prone and silently missed by the next feature. Changing the shared role query changes semantics for every caller. A login-only check misses stale tokens. |
