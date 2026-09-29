# Implementation Plan: Patient Self-Service Fixed-Time Booking

**Branch**: `021-patient-self-service-booking` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/021-patient-self-service-booking/spec.md`

## Summary

Give an authenticated Patient Account holder (039) a self-service way to list a clinic's
currently-OPEN Fixed-Time Slots and book one for themselves. Reuses 015's fee
resolution/locking, 019's Patient auto-creation/phone-linking, and 016's Slot/Booking data
model and race-closure pattern exactly as-is; the only genuinely new backend surface is (a)
a patient-facing JWT authentication filter — the first authenticated endpoint this identity
system has ever needed — and (b) a slot-listing query plus a `PatientBookingService` that is
016's `StaffBookingService` with authorization removed and patient resolution swapped for
`PatientLinkingService.findOrCreatePatient`.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript/React (frontend) — matches the rest of this codebase.

**Primary Dependencies**: Spring Boot, Spring Security, Spring Data JPA, `jjwt` (already used by `com.cms.patient.account.JwtService`); React + Vite + Tailwind CSS (frontend, matching 016's `staff-booking` feature).

**Storage**: PostgreSQL via Flyway migrations — no new tables; reuses `slot`, `booking`, `patient`, `appointment_type`.

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL 16-alpine) + MockMvc for backend; Vitest + Testing Library for frontend — matches every prior feature this session.

**Target Platform**: Existing Spring Boot backend service + React SPA frontend.

**Project Type**: Web application (backend + frontend), extending existing `com.cms.booking`, `com.cms.scheduling`, and `com.cms.patient.account` modules.

**Performance Goals**: N/A beyond existing system norms — a single-request list/book round trip, no batch or high-throughput concern.

**Constraints**: Must not weaken or duplicate 015's fee-resolution-first-write-gate ordering or 016/019's data-layer race-closure guarantees (Constitution IV). Must not introduce a second, competing patient authentication mechanism (Constitution III / prior FR-008 "no shared authentication logic" precedent from 039).

**Scale/Scope**: Two new HTTP endpoints, one new security filter, no new entities, no new migration.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Test-First Development**: PASS (planned) — tasks.md will include integration tests for listing, booking, race-closure, fee-block, first-booking auto-link, and auth-filter behavior, written before implementation, per every prior feature this session.
- **II. Simplicity & YAGNI**: PASS — no new entity, no new migration, no new security chain (extends the existing `com.cms.patient.account.SecurityConfig` chain rather than adding a competing one); the list endpoint deliberately omits fee amounts (fee resolution/locking stays exclusively 015's/booking-time's responsibility, not duplicated into a read path).
- **III. Modular, Library-First Architecture**: PASS — `PatientLinkingService` (019) and `FeeResolutionService` (015) are reused via their existing service contracts, not reimplemented; the new patient JWT filter lives in `com.cms.patient.account`, the new booking/listing logic in `com.cms.booking`, matching each module's existing ownership.
- **IV. Data Privacy & Integrity by Design**: PASS — reuses 016's proven `saveAndFlush` + `uq_booking_slot` unique-constraint race closure at the data layer (not just application-layer), and 019's `PatientLinkingService`'s own already-proven data-layer race closure for concurrent first-booking Patient-record creation.

No violations. Complexity Tracking section not needed.

## Project Structure

### Documentation (this feature)

```text
specs/021-patient-self-service-booking/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md         # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── patient-booking.md
└── tasks.md             # Phase 2 output (/speckit-tasks — not created here)
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/cms/
│   ├── patient/account/
│   │   ├── PatientJwtAuthenticationFilter.java   # NEW - mirrors StaffJwtAuthenticationFilter
│   │   ├── PatientAuthenticationEntryPoint.java  # NEW - mirrors StaffAuthenticationEntryPoint
│   │   └── SecurityConfig.java                   # EXTENDED - wires the filter in, adds explicit matchers
│   ├── booking/
│   │   ├── PatientBookingService.java             # NEW
│   │   ├── PatientBookingController.java          # NEW
│   │   ├── dto/
│   │   │   ├── PatientBookSlotRequest.java        # NEW
│   │   │   └── OpenSlotResponse.java              # NEW
│   │   └── BookingExceptionHandler.java           # EXTENDED (if any new exception types)
│   └── scheduling/
│       └── SlotRepository.java                    # EXTENDED - one new open-fixed-time-slots query
└── src/test/java/com/cms/booking/integration/
    ├── AbstractPatientBookingIntegrationTest.java # NEW fixture
    ├── PatientOpenSlotListingTest.java             # NEW
    ├── PatientBookingExistingLinkTest.java         # NEW
    ├── PatientBookingFirstTimeLinkTest.java        # NEW
    ├── PatientBookingFeeBlockTest.java             # NEW
    ├── PatientBookingAlreadyBookedTest.java        # NEW
    └── PatientBookingAuthenticationTest.java       # NEW

frontend/
├── src/features/patient-account/
│   ├── token.ts        # NEW - sessionStorage-backed Patient Account session (mirrors staff-login/token.ts);
│   │                   #       no prior patient-authenticated frontend feature has needed this
│   └── LoginForm.tsx   # EXTENDED - persists the session via token.ts on successful login
├── src/features/patient-booking/
│   ├── api.ts             # NEW
│   ├── OpenSlotList.tsx   # NEW
│   └── BookSlotForm.tsx   # NEW
└── tests/patient-booking/
    ├── OpenSlotList.test.tsx  # NEW
    └── BookSlotForm.test.tsx  # NEW
```

**Structure Decision**: Extends the existing web application's `backend/` (Spring Boot,
Java) and `frontend/` (React/Vite) trees along already-established module boundaries — no
new top-level project or module. New backend code splits across `com.cms.patient.account`
(the new patient JWT filter, since authentication is that module's exclusive concern per
039's precedent) and `com.cms.booking` (the booking/listing logic, since `Booking`,
`AppointmentType`, and `FeeResolutionService` already live there per 015/016). On the
frontend, the same authentication gap shows up in miniature: no patient-authenticated
feature has shipped before this one, so 039's `LoginForm.tsx` has never needed to persist
its token — this feature adds that missing `token.ts` (039's module, not this feature's,
since a Patient Account session belongs with the identity it authenticates) alongside its
own new `patient-booking` feature directory.

## Complexity Tracking

*No Constitution Check violations — this section is not needed.*
