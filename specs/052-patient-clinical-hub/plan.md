# Implementation Plan: Patient Context & Clinical History Hub

**Branch**: `052-patient-clinical-hub` | **Date**: 2026-09-15 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/052-patient-clinical-hub/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Add a new `PatientHubPage` (`/staff/clinics/:clinicId/patients/:patientId`), reachable from `PatientSearch.tsx`, with 5 sections (Overview, Bookings, Consultations, Prescriptions, External Records). Overview/Bookings need one new minimal backend query each: a clinic+patient-scoped booking list (`BookingRepository.findByPatient_IdOrderBySlot_Session_SessionDateDesc`, mirroring the existing patient-account-scoped query's own derived-method convention) exposed via a new `PatientBookingHistoryController`, and an `anonymizedAt` field added to the already-shared `PatientSearchResultResponse` (no new endpoint, no migration). Consultations/Prescriptions/External Records sections render the same booking list as navigable rows into the existing, unmodified per-booking pages — a navigation hub, not a content-aggregation hub, so the existing treating-doctor authorization (030) is reused byte-for-byte with zero new authorization code (research.md).

## Technical Context

**Language/Version**: Java 21 / Spring Boot 3.3.5 (backend); TypeScript 5 / React 18 (frontend) — existing stack, no change

**Primary Dependencies**: No new dependency. Backend: Spring Data JPA derived query method (same convention as the existing `findByPatient_PatientAccount_IdOrderByCreatedAtDesc`). Frontend: 046/047's component library, existing `fetch`-based API client pattern, `react-router-dom` for tabs (via nested routes or local state — decided in research.md).

**Storage**: PostgreSQL (existing) — no schema change, no migration. Reads existing `Booking`/`Patient`/`Slot`/`Session` columns only; `anonymizedAt` is an already-persisted column simply not yet exposed in any response DTO.

**Testing**: Backend: Mockito unit test + `@WebMvcTest` contract test for the new endpoint (`backend/src/test/java/com/cms/patient/record/{unit,contract}/`). Frontend: Vitest + React Testing Library.

**Target Platform**: Web (existing Vite/React SPA + Spring Boot API).

**Project Type**: Web application (frontend + backend — second full-stack feature in this wave, after 048).

**Performance Goals**: The new booking-list query is a single indexed lookup by `patient_id` (already an indexed FK on `Booking`), paginated — no new performance concern.

**Constraints**: Zero new visual tokens (reuse `DESIGN.md`/046/047); zero change to any existing per-booking page's behavior or authorization (FR-008); zero new authorization logic — the hub only ever links into pages that already enforce their own checks (FR-006).

**Scale/Scope**: 1 new backend endpoint (patient booking list), 1 DTO field addition (`anonymizedAt`), 1 new frontend route + page with 5 sections, 1 new Patient Search navigation link.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Test-First)**: The new booking-list endpoint is genuinely new backend logic — a failing contract test (clinic-scoping, pagination, ordering) and unit test are written alongside implementation, same discipline as 048's `TodaySessionStatsController`.
- **Principle II (Simplicity & YAGNI)**: PASS — the navigation-hub design (research.md Decision 1) is specifically chosen because a content-aggregation hub would require re-implementing `TreatingDoctorAuthorizationService`'s check at a new aggregate layer, a real duplication FR-006 forbids; the DTO field addition reuses `PatientSearchResultResponse` rather than inventing a parallel "hub overview" DTO.
- **Principle III (Modular, Library-First Architecture)**: PASS — the new endpoint lives in `com.cms.patient.record` (already owns `Patient`, `PatientDetailController`, `ClinicPatientSearchController`); it reads `Booking` (an existing, established `patient.record → booking` read dependency, same direction `PatientAnonymizationService`'s existing `existsActiveFutureBookingForPatient` check already uses) — no new module, no new cross-module event.
- **Principle IV (Data Privacy & Integrity by Design)**: PASS — this is the feature most directly governed by this principle: FR-006/FR-008 exist specifically to keep the write-once clinical-documentation guarantee and the treating-doctor authorization fully intact; FR-002/FR-003/SC-003 keep anonymization and clinic-scoping accurate. No new data stored.

No violations — Complexity Tracking table not needed.

## Project Structure

### Documentation (this feature)

```text
specs/052-patient-clinical-hub/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md         # Phase 1 output (/speckit-plan command)
├── contracts/             # Phase 1 output (/speckit-plan command)
└── tasks.md               # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/cms/patient/record/
│   ├── dto/PatientSearchResultResponse.java   # EDIT - add anonymizedAt
│   ├── dto/PatientBookingSummaryResponse.java # NEW
│   └── api/PatientBookingHistoryController.java  # NEW
├── src/main/java/com/cms/booking/repository/BookingRepository.java  # EDIT - add findByPatient_Id...
└── src/test/java/com/cms/patient/record/
    ├── unit/PatientBookingHistoryControllerTest.java   # NEW (Mockito)
    └── contract/PatientBookingHistoryControllerContractTest.java  # NEW (@WebMvcTest)

frontend/
├── src/
│   ├── features/patient-search/
│   │   ├── api.ts                          # EDIT - add anonymizedAt to PatientSearchResultResponse type, add listPatientBookings
│   │   └── PatientSearch.tsx                # EDIT - link each result into the hub
│   └── routes/staff/
│       ├── PatientHubPage.tsx                # NEW
│       └── ClinicToolPages.tsx (or App.tsx)   # EDIT - new route
└── tests/patient-search/
    ├── PatientSearch.test.tsx                 # EDIT - new nav-link assertion
    └── PatientHubPage.test.tsx                 # NEW
```

**Structure Decision**: Backend work confined to `com.cms.patient.record` (new controller/DTOs, owns `Patient`) plus one additive method on `com.cms.booking.repository.BookingRepository` (the existing owner of `Booking` — `patient.record` already has an established, one-way read dependency on `booking` via `PatientAnonymizationService`'s existing `existsActiveFutureBookingForPatient` check, so this follows the same precedent rather than creating a new cross-module edge). Frontend work is one new page plus edits to the existing `patient-search` feature.

## Complexity Tracking

*No violations — table not needed.*
