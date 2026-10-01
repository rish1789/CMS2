# Implementation Plan: Per-Clinic Fees

**Branch**: `claude/068-per-clinic-fees` | **Date**: 2026-10-01 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/068-per-clinic-fees/spec.md`

## Summary

This closes SEC-03 (owner decision B). Prices move from the doctor to (doctor, **clinic**), in two new tables with data-layer uniqueness:
- `clinic_doctor_fee`: the default fee;
- `clinic_appointment_type_price`: the per-type price.

A data migration copies today's doctor-wide prices to every clinic where the doctor holds an active Doctor role, so every fee resolves exactly as before on day one.

`FeeResolutionService` takes the booking's clinic, with no cross-clinic fallback. Price writes move to clinic-scoped endpoints that only that clinic's active ClinicAdmin may call; the old doctor-wide price writes are refused with `FEE_MOVED_TO_CLINIC`. Readiness, the patient appointment-type listing, the doctor delete guard, the clinic permanent delete and the admin price screen all become clinic-aware.

## Technical Context

- **Language/Version:** Java 21; TypeScript (React 18, Vite).
- **Primary Dependencies:** Spring Boot 4.1 (Data JPA, Security), Flyway; React Router 7, Vitest 5.
- **Storage:** PostgreSQL 16. New migrations **V42** (tables) and **V43** (copy). The shipped V9 is untouched.
- **Testing:** JUnit 5 + Mockito unit tests, `@WebMvcTest` contract tests and Testcontainers integration tests; Vitest for the frontend.
- **Target Platform:** Linux server plus a browser SPA.
- **Project Type:** Web application (backend and frontend).
- **Performance Goals:** One extra indexed lookup per fee resolution at most, which is negligible.
- **Constraints:**
  - No change to the resolution order, the hard block or fee locking.
  - Tenant isolation: a clinic's prices are only ever read or written in that clinic's context.
- **Scale/Scope:**
  - 2 tables and 2 migrations.
  - 1 service signature change, used by 5 callers.
  - 4 new endpoints and 2 retired writes.
  - 1 readiness change, 2 guard changes and 1 admin screen.
  - The booking forms read a new response field.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle / Gate | Status | Evidence |
|---|---|---|
| I. Test-first | PASS | research.md R6 lists the red-first tests. The migration copy has its own integration test proving equivalence, as required for invariant-enforcing migrations. |
| II. Simplicity | PASS | Two plain tables keyed on their natural uniques. No pricing engine or history. The old columns are left in place, not reworked. |
| III. Module boundaries | PASS | Everything stays in `booking` (fees) plus `identity/admin` guard hooks, using the existing repositories and patterns. |
| IV. Data integrity | PASS | Data-layer unique keys on (clinic, doctor) and (clinic, type). `CHECK amount >= 0`. Locked booking fees untouched. No patient data involved. |
| Multi-tenancy | PASS | **This feature is a tenant-isolation fix.** Every new query and endpoint is clinic-scoped, and writes require that clinic's admin. |
| Rationale note (booking/fee logic) | PASS | It changes 017 FR-005 to FR-007 (doctor-wide fees, editable by any of the doctor's clinics) to clinic-scoped prices, per the owner's SEC-03 decision B and backlog 015 ("every clinic sets fees"). 015's resolution order, hard block and locking are unchanged. |
| Flyway review | PASS (pending code review) | V42 is additive (new tables). V43 inserts only, copying from old tables. Both are forward-only and multi-tenant safe, since every row carries its clinic. |

**Post-design re-check:** PASS, with no violations.

## Project Structure

### Documentation (this feature)

```text
specs/068-per-clinic-fees/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── contracts/clinic-fees-api.md
├── checklists/requirements.md
└── tasks.md            # /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/resources/db/migration/
├── V42__create_clinic_fee_tables.sql                 # NEW
└── V43__copy_doctor_fees_to_active_clinics.sql       # NEW (insert-only copy)

backend/src/main/java/com/cms/booking/
├── domain/ClinicDoctorFee.java, ClinicAppointmentTypePrice.java     # NEW entities
├── repository/ClinicDoctorFeeRepository.java, ClinicAppointmentTypePriceRepository.java   # NEW
├── service/FeeResolutionService.java                 # resolve(clinicId, doctorProfileId, typeId)
├── service/ClinicFeeService.java                     # NEW: get/set/remove + ClinicAdmin-of-clinic rule
├── api/ClinicFeeController.java                      # NEW: /api/v1/clinics/{clinicId}/doctors/{id}/fees/**
├── api/BookingController.java, service/AppointmentTypeService.java  # retire doctor-wide price writes
├── service/{Patient,PatientQueue,Staff,StaffQueue}BookingService.java, FrontDeskWalkInService.java  # pass clinicId
├── service/DoctorBookingReadinessService.java        # clinic tables
├── dto/AppointmentTypeResponse.java                  # feeOverride -> fee (effective at clinic)
└── api/PatientBookingController.java                 # clinic-scoped appointment-type listing

backend/src/main/java/com/cms/identity/admin/service/{DoctorVerificationService,ClinicVerificationService}.java  # guard and cleanup
backend/src/main/java/com/cms/common/OpenApiConfig.java                     # fix the "/api/v1/doctors/** (public)" doc label

frontend/src/features/appointment-types/   # clinic-scoped price editing
frontend/src/features/{patient-booking,staff-booking,waitlist,front-desk-walk-in}/  # read `fee`
```

**Structure Decision**: This uses the existing package-per-feature layout. Fees stay in `booking`. The guard hooks live where their services already are.

## Complexity Tracking

No violations.
