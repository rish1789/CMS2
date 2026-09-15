# Implementation Plan: Fee Resolution & Locking at Booking Time

**Branch**: `017-fee-resolution-locking` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/017-fee-resolution-locking/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

A new `com.cms.booking` module (the backlog's own listed module for this feature) with two new entities (`AppointmentType`, `DoctorDefaultFee`), a `FeeResolutionService.resolve(doctorProfileId, appointmentTypeId)` — the reusable capability 016/017/018 will call once built — and two configuration endpoints (create/list Appointment Types, set default fee) under a new `/api/v1/doctors/**` security chain reusing the existing staff-JWT machinery. No `Booking` entity, no payment-status, no frontend — all explicitly out of this feature's scope per build-order.md's placement of the booking-creation features strictly after it.

## Technical Context

**Language/Version**: Java 21 (backend only — no frontend surface; spec Assumptions).

**Primary Dependencies**: Spring Boot 3.x (Data JPA, Web, Security) — reuses `com.cms.identity.doctor.DoctorProfile`/`DoctorProfileRepository` (005/007), `com.cms.identity.account.RoleAssignment`/`RoleAssignmentRepository` (004, extended with one new query method), and the existing `StaffJwtService`/`StaffJwtAuthenticationFilter`/`StaffAuthenticationEntryPoint` beans (001/004) for a new, non-overlapping security chain. No new external dependency.

**Storage**: PostgreSQL — one new migration (`V9`): `appointment_type` and `doctor_default_fee` tables.

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers + MockMvc (backend only).

**Target Platform**: Linux container (Docker).

**Project Type**: Backend service module with two REST endpoints (no frontend in this feature).

**Performance Goals**: Same order of magnitude as prior features.

**Constraints**:
- `AppointmentType`/`DoctorDefaultFee` are doctor-scoped, not clinic-scoped (spec Scope Decisions) — their path prefix (`/api/v1/doctors/**`) doesn't overlap `/api/v1/clinics/**`, so it needs its own `SecurityFilterChain` (a request matching no declared chain's `securityMatcher` bypasses Spring Security's filter entirely and would be unintentionally unprotected — this is why every path prefix in this codebase has always gotten an explicit chain or matcher, and this feature is no exception).
- `FeeResolutionService.resolve` MUST NOT create, modify, or reference any Booking/payment-status data (FR-009) — none exists yet, and this method's only reads are `AppointmentType`/`DoctorDefaultFee`/`DoctorProfile`.
- Money fields MUST use `BigDecimal` (precision 10, scale 2), not floating-point, to avoid rounding error in a currency-shaped field — no monetary field exists anywhere else in this codebase yet, so this establishes the pattern.

**Scale/Scope**: Single feature — 2 new entities, 1 migration, 2 new repositories, 1 new service (`FeeResolutionService`) + 1 new config service (`AppointmentTypeService`), 1 new controller, 1 new security chain, 2 new endpoints, 1 extended repository method on `RoleAssignmentRepository`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Test-First Development | PASS | Tests for every FR (resolution order, hard block, wrong-doctor rejection, both authorized-actor paths, forbidden path) written before implementation. |
| II. Simplicity & YAGNI | PASS | No Booking entity, no payment machinery, no frontend built ahead of the features that actually need them (spec Scope Decisions); `AppointmentType`'s scoping reuses `DoctorProfile`'s own existing global-not-per-clinic precedent rather than inventing new scoping rules. |
| III. Modular, Library-First Architecture | PASS | New `com.cms.booking` module — Constitution's own named example ("booking" listed alongside scheduling/waitlist/clinical-documentation/notifications/discovery). Reads `DoctorProfile`/`RoleAssignment` read-only; owns all its own mutable state (no writes back into `com.cms.identity`). |
| IV. Data Privacy & Integrity by Design | PASS | The hard-block-not-silent-default rule (FR-003) is itself a financial-integrity guarantee, enforced at the point of resolution, not deferred to a later, easier-to-miss check. |

No violations — Complexity Tracking is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/017-fee-resolution-locking/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── fee-resolution.md
└── tasks.md
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/cms/booking/
│   ├── AppointmentType.java                    # new entity
│   ├── DoctorDefaultFee.java                   # new entity
│   ├── AppointmentTypeRepository.java          # new
│   ├── DoctorDefaultFeeRepository.java         # new
│   ├── FeeResolutionService.java               # new — the feature's core exported contract
│   ├── AppointmentTypeService.java             # new — create/list + set-default-fee, shared authorization
│   ├── BookingController.java                  # new — POST/GET appointment-types, PUT default-fee
│   ├── BookingExceptionHandler.java             # new
│   ├── BookingSecurityConfig.java               # new — @Order(6), /api/v1/doctors/**
│   ├── DoctorProfileNotFoundException.java      # new (local, mirrors com.cms.scheduling's pattern)
│   ├── AppointmentTypeNotFoundException.java    # new
│   ├── NoFeeConfiguredException.java            # new
│   ├── ForbiddenException.java                  # new (local)
│   └── dto/
│       ├── CreateAppointmentTypeRequest.java
│       ├── AppointmentTypeResponse.java
│       └── SetDefaultFeeRequest.java
├── src/main/java/com/cms/identity/account/
│   └── RoleAssignmentRepository.java            # extended: +findByAccount_IdAndRoleAndActiveTrue
├── src/main/resources/db/migration/
│   └── V9__create_appointment_type_and_default_fee.sql   # new
└── src/test/java/com/cms/booking/integration/
    ├── AbstractBookingIntegrationTest.java
    ├── FeeResolutionOrderTest.java
    ├── FeeResolutionHardBlockTest.java
    ├── FeeResolutionWrongDoctorTest.java
    ├── AppointmentTypeConfigAuthorizationTest.java
    └── SetDefaultFeeTest.java
```

**Structure Decision**: A new top-level `com.cms.booking` module — Constitution III's own named example of a module boundary, and the backlog's own stated module for this feature. A new `BookingSecurityConfig` chain (`@Order(6)`) since `/api/v1/doctors/**` doesn't overlap any existing chain's path prefix. No `frontend/` changes (spec Assumptions).

## Post-Design Constitution Re-Check

Re-evaluated after Phase 1 (data model + contracts + quickstart, below): all four principles still PASS. The read-only cross-module reference design (data-model.md) confirms Principle III holds in the detailed design.

## Complexity Tracking

*No violations — table intentionally left empty.*
