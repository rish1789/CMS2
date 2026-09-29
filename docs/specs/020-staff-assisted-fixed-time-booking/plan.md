# Implementation Plan: Staff-Assisted Fixed-Time Booking

**Branch**: `020-staff-assisted-fixed-time-booking` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/020-staff-assisted-fixed-time-booking/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Defines `Booking` (first time, `com.cms.booking` — 015's module) and adds `BOOKED` to 012's `SlotStatus`. `StaffBookingService.bookSlot(...)` orchestrates: authorize (Operations/ClinicAdmin at the Slot's clinic) → validate the Slot is `OPEN` → resolve-and-lock the fee via 015's existing `FeeResolutionService.resolve` (first real side-effecting checkpoint — nothing is written before this succeeds) → resolve-or-create the Patient (009's existing `Patient` entity, walk-in path uses its existing nullable-`patientAccount` constructor directly) → save the `Booking` → flip the Slot to `BOOKED`. One new endpoint, `POST /api/v1/clinics/{clinicId}/slots/{slotId}/book`, under the existing `/api/v1/clinics/**` chain (one new explicit matcher). A minimal frontend form (spec Assumptions).

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript/React (frontend — a minimal staff-facing booking form; spec Assumptions).

**Primary Dependencies**: Spring Boot 3.x (Data JPA, Web, Security) — reuses `com.cms.scheduling.Slot`/`SlotRepository`/`Session` (012), `com.cms.booking.FeeResolutionService`/`AppointmentTypeRepository` (015), `com.cms.patient.record.Patient`/`PatientRepository` (009), `com.cms.identity.clinic.Clinic`/`ClinicRepository` (001), `com.cms.identity.account.RoleAssignmentRepository` (004), and `com.cms.common.IndianMobileNumberValidator` directly. No new external dependency.

**Storage**: PostgreSQL — one new migration (`V12`): `booking` table (unique `slot_id`) and `slot.status` gains an allowed value at the application-enum level only (no DB-level enum type change needed — `status` is already a plain `VARCHAR`).

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers + MockMvc (backend). Frontend: mirrors 013's `ScheduleForm` test pattern.

**Target Platform**: Linux container (Docker).

**Project Type**: Extension of the existing `com.cms.booking` module (015) plus one new endpoint on the existing `/api/v1/clinics/**` chain; small new frontend feature folder.

**Performance Goals**: Same order of magnitude as prior features — one booking is a handful of reads plus 1–2 writes.

**Constraints**:
- Fee resolution MUST be the first side-effecting step attempted in the booking flow — no Patient row, no Slot mutation, no Booking row may be written before it succeeds (FR-004, spec Scope Decisions) — achieved by ordering within one `@Transactional` method, so any failure rolls back everything attempted so far.
- Exactly one `Booking` may ever exist per `Slot` — enforced by a DB-level unique constraint on `booking.slot_id`, not merely an application-level `status == OPEN` check (Constitution IV) — the application check remains as the fast, common-case rejection in front of it.
- The new endpoint MUST get an explicit matcher on the existing `/api/v1/clinics/**` chain — a path under that prefix with no explicit matcher silently falls through to `anyRequest().permitAll()` (the exact gap already caught and fixed once this session, in 016-schedule-edit-non-retroactivity).
- `paymentStatus` is set to `PENDING` at creation and has no mutator exposed by this feature (spec Assumptions).

**Scale/Scope**: Single feature — 1 new entity (`Booking`), 1 new enum (`PaymentStatus`), 1 migration, 1 repository, 1 service, 1 controller, 1 new security matcher, 4 new exceptions, 1 small frontend feature.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Test-First Development | PASS | Tests for every FR (existing-patient booking, walk-in-patient creation + phone validation, fee-block-creates-nothing, already-booked rejection incl. concurrency, authorization) written before implementation. |
| II. Simplicity & YAGNI | PASS | No payment-gateway/toggle machinery, no atomic-reschedule, no Queue-mode path (all explicitly out of scope per source material and 018's own future ownership); reuses 009/012/015's existing entities/services directly rather than duplicating any of them. |
| III. Modular, Library-First Architecture | PASS | `Booking` lives in `com.cms.booking` (015's own module, matching the backlog's own "Booking" module label); reads `Slot`/`Patient`/`Clinic`/`RoleAssignment` as read-only cross-module references, the established pattern. |
| IV. Data Privacy & Integrity by Design | PASS | The one-Booking-per-Slot DB constraint is exactly Principle IV's "close duplicate-creation races at the data layer" requirement; fee-resolution-first ordering is itself a financial-integrity guarantee (no walk-in Patient or Slot state change survives a blocked booking). |

No violations — Complexity Tracking is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/020-staff-assisted-fixed-time-booking/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── staff-booking.md
└── tasks.md
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/cms/scheduling/
│   └── Slot.java                              # extended: +setStatus, SlotStatus gains BOOKED
├── src/main/java/com/cms/booking/
│   ├── PaymentStatus.java                      # new enum (PENDING, PAID)
│   ├── Booking.java                             # new entity
│   ├── BookingRepository.java                   # new
│   ├── StaffBookingService.java                 # new
│   ├── StaffBookingController.java              # new
│   ├── SlotNotFoundException.java               # new
│   ├── SlotAlreadyBookedException.java           # new
│   ├── PatientNotFoundException.java             # new
│   ├── InvalidMobileNumberException.java         # new (local, mirrors identity.clinic's/patient.account's own copies)
│   └── dto/
│       ├── BookSlotRequest.java
│       └── BookingResponse.java
├── src/main/java/com/cms/identity/account/
│   └── SecurityConfig.java                      # extended: +1 matcher for POST /api/v1/clinics/*/slots/*/book
├── src/main/resources/db/migration/
│   └── V12__create_booking.sql                  # new
└── src/test/java/com/cms/booking/integration/
    ├── AbstractStaffBookingIntegrationTest.java
    ├── StaffBookingExistingPatientTest.java
    ├── StaffBookingWalkInPatientTest.java
    ├── StaffBookingFeeBlockTest.java
    ├── StaffBookingAlreadyBookedTest.java
    └── StaffBookingAuthorizationTest.java

frontend/
├── src/features/staff-booking/
│   ├── BookSlotForm.tsx
│   └── api.ts
└── tests/staff-booking/
    └── BookSlotForm.test.tsx
```

**Structure Decision**: `Booking` and its supporting types live in the existing `com.cms.booking` module (015) — not a new module — since the backlog labels this feature's module "Booking" too, and it directly extends 015's `FeeResolutionService`. `Slot` (012, `com.cms.scheduling`) is extended in place. A new `frontend/src/features/staff-booking/` folder follows the one-folder-per-feature convention.

## Post-Design Constitution Re-Check

Re-evaluated after Phase 1 (data model + contracts + quickstart, below): all four principles still PASS. The single-transaction, fee-first ordering (data-model.md) confirms Principle IV's no-partial-state guarantee holds in the detailed design.

## Complexity Tracking

*No violations — table intentionally left empty.*
