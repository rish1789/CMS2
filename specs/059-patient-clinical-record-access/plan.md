# Implementation Plan: Patient Clinical Record Access

**Branch**: `059-patient-clinical-record-access` | **Date**: 2026-09-22 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/059-patient-clinical-record-access/spec.md`

## Summary

Add a patient-facing, strictly read-only path to the three existing clinical documentation types
(Consultation Note, Prescription, External Record Reference), scoped to bookings that resolve to
the signed-in patient's own account via the exact same `Patient.patientAccount` linkage the
existing "My Bookings" feature already uses. New patient-realm GET endpoints reuse the existing
response DTOs unchanged (patients see the same content doctors see) and reuse
`RetentionPurgeService`'s existing hard-delete behavior for free (a purged record is simply absent
from the same lookup, no new filtering needed). A new bulk "which of my bookings have a record"
endpoint lives in the `clinical` module (not `booking`) specifically to avoid introducing a
`booking → clinical` dependency where only `clinical → booking` exists today.

## Technical Context

**Language/Version**: Java 21 (backend, Spring Boot 3.3), TypeScript 5 / React 19 (frontend, Vite)

**Primary Dependencies**: Spring Data JPA/Hibernate — no new dependency of any kind

**Storage**: PostgreSQL — no new migration. No new column, table, or index; every new query reads
existing tables through existing/derived-query repository methods.

**Testing**: JUnit 5 + Mockito (unit), `@WebMvcTest` (contract), Testcontainers (integration,
written/compiled but unexecuted in this sandbox per the project's standing Docker limitation) on
the backend; Vitest + Testing Library on the frontend

**Target Platform**: Existing web app (Spring Boot backend on :8080, Vite/React frontend on :5173)
— no new platform

**Project Type**: Web application (existing `backend/` + `frontend/` structure)

**Performance Goals**: The bulk availability check must be a single batched query per page of
bookings (no N+1) — mirrors this codebase's own established `countBySessionIdIn`-style bulk-query
precedent (day-sheet list, 042-day-sheet-hardening).

**Constraints**: Read-only — no new write path anywhere in this feature (spec FR-006). No new
module: the three record types stay exactly where they are (`com.cms.clinical`); only new read
endpoints and service methods are added. Must not create a module dependency cycle between
`booking` and `clinical` (Constitution III).

**Scale/Scope**: Touches only the `clinical` module (new patient-facing controller(s), three new
service methods mirroring the existing `get`/`list` methods, one new bulk-availability method, one
new repository method per clinical repository) and the `booking` module (one new derived-query
method on `BookingRepository` to load-and-authorize a booking by patient account), plus the
frontend's patient-facing feature area (a new "visit record" view and a "My Bookings" list
enhancement). No new module, no schema change.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Test-First Development**: Every new service method and endpoint below gets unit/contract
  coverage before/with implementation, mirroring the existing `ConsultationNoteService`/
  `PrescriptionService`/`ExternalRecordReferenceService` test shapes already in this codebase.
  Integration tests are written/compiled per this project's standing convention even though they
  can't execute in this sandbox. **PASS**.
- **II. Simplicity & YAGNI**: No new DTOs where an existing response shape already fits (patients
  see the exact same `ConsultationNoteResponse`/`PrescriptionResponse`/
  `ExternalRecordReferenceResponse` a doctor sees, per spec.md's own Assumptions). No new
  abstraction layer — new service methods are added directly to the three existing services,
  reusing `TreatingDoctorAuthorizationService`'s sibling pattern with a patient-account check
  instead of a treating-doctor check, not a new generic "authorization strategy" framework.
  **PASS**.
- **III. Modular, Library-First Architecture**: The bulk "has a clinical record" availability
  check is placed inside `com.cms.clinical` (not `com.cms.booking`), specifically because
  `clinical` already depends on `booking` (every existing service takes a `Booking`) — adding a
  reverse `booking → clinical` call to enrich `PatientBookingSummaryResponse` would create a
  module cycle. Keeping the new capability as its own endpoint in `clinical`, called separately
  by the frontend, avoids that cycle structurally rather than by convention. See research.md
  Decision 3. **PASS**.
- **IV. Data Privacy & Integrity by Design**: This feature adds no new write path and no new
  patient-identifying field — it only extends who may read data that already exists under the
  DPDP retention/anonymization lifecycle this codebase already enforces (038's
  `RetentionPurgeService`). A purged record is a deleted row; the new patient-facing lookups use
  the exact same `findByBooking_Id`-style queries the existing treating-doctor lookups use, so a
  purged record is already, automatically, invisible to a patient the same way it's invisible to
  staff — no new filtering logic needed, and no risk of the two surfaces drifting out of sync
  (research.md Decision 2). Consultation Notes/Prescriptions remain write-once/immutable — this
  feature adds no edit path for any role, patient included (spec.md FR-006). **PASS**.
- **Multi-tenancy**: Every new endpoint stays scoped to bookings resolvable to the caller's own
  patient account — the same clinic-spanning "my bookings across every clinic" scope the existing
  `GET /api/v1/patients/bookings` endpoint already has (a patient's own data is intentionally not
  clinic-siloed, matching that existing endpoint's own precedent). **PASS**.
- **Out-of-scope boundaries**: No payments, uploads, notifications, or reschedule touched. No
  download/export/print (spec.md FR-010) — explicitly out of scope, not deferred silently.
  **PASS**.

No violations to justify — Complexity Tracking table is intentionally omitted below.

**Post-Design Re-check** (after Phase 0/1 artifacts below): still **PASS** on every principle. The
one design decision most worth re-confirming — keeping the availability check inside `clinical`
rather than embedding it in the existing `PatientBookingSummaryResponse` — was reinforced, not
weakened, once the data-model/contracts were written out in Phase 1: it also means the existing,
already-shipped `PatientMyBookingsController`/`PatientBookingSummaryResponse` need zero changes,
which is additionally smaller than the original informal idea of extending that DTO.

## Project Structure

### Documentation (this feature)

```text
specs/059-patient-clinical-record-access/
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
├── booking/
│   └── repository/
│       └── BookingRepository.java                  # + findByIdAndPatient_PatientAccount_Id()
└── clinical/
    ├── service/
    │   ├── ConsultationNoteService.java             # + getForPatient(bookingId, patientAccountId)
    │   ├── PrescriptionService.java                 # + listForPatient(bookingId, patientAccountId)
    │   ├── ExternalRecordReferenceService.java      # + listForPatient(bookingId, patientAccountId)
    │   └── ClinicalRecordAvailabilityService.java   # NEW — bulk "which booking ids have a record"
    └── api/
        └── PatientClinicalRecordController.java     # NEW — the 4 new GET endpoints (note/
                                                        prescriptions/external-records/availability)

frontend/src/
├── features/
│   ├── patient-clinical-records/
│   │   ├── api.ts                                    # NEW — the 4 new client calls
│   │   └── VisitRecordSection.tsx                    # NEW — note + prescriptions + external records, one visit
│   └── patient-bookings/
│       └── MyBookings.tsx                             # existing — + an availability indicator per row
└── routes/patient/
    └── PatientPages.tsx                               # PatientBookingDetailPage (existing route/page,
                                                          already the single click-through target from
                                                          MyBookings) + renders VisitRecordSection alongside
                                                          its existing QueuePositionIndicator/CancelBookingButton
```

**Structure Decision**: Existing `backend/` + `frontend/` web-application layout, unchanged. All
new backend code lives in the `clinical` module that already owns these three record types
(Constitution III) plus one new derived-query method on `booking`'s own `BookingRepository`
(booking already owns booking-ownership queries; this is additive to that, not new ground). No new
module, no schema change.

## Complexity Tracking

*No Constitution Check violations — table intentionally omitted.*
