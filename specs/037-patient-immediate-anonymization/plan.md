# Implementation Plan: Patient Immediate Anonymization

**Branch**: `037-patient-immediate-anonymization` | **Date**: 2026-09-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/037-patient-immediate-anonymization/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Let an authorized staff member immediately scrub a clinic-scoped Patient record's identifying
fields (`name`, `phone`) on request — DPDP's first, on-demand deletion tier — blocked while any
active future booking exists, and recording a durable `anonymizedAt` marker the future monthly
purge (034) will check. Technical approach: extends `com.cms.patient.record` (which already owns
`Patient`/`PatientRepository`) with the new field, a new `BookingRepository` query for the
"active future booking" precondition (read-only, mirrors 033/deverification-cascade's own
identical Slot-status-based definition), a `PatientAnonymizationService`, and one thin staff
endpoint — no new module, no event.

## Technical Context

**Language/Version**: Java 21 (backend) — this feature is backend-only, no frontend surface stated (a staff-only compliance action; adding a minimal admin UI is included as this session's own consistent minimum, mirroring 033/deverification-cascade's `PendingDoctorsList.tsx` extension).

**Primary Dependencies**: Spring Boot, Spring Data JPA, Spring Security (staff JWT chain); React + Tailwind CSS on the frontend.

**Storage**: PostgreSQL via a new Flyway migration (`V22`) — adds `patient.anonymized_at`.

**Testing**: JUnit 5 + Spring Boot Test (`@WebMvcTest`/`@SpringBootTest` + Testcontainers) on the backend; Vitest/React Testing Library on the frontend.

**Target Platform**: Linux server (Spring Boot backend), browser (React frontend).

**Project Type**: Web application (`backend/` + `frontend/`, existing structure).

**Performance Goals**: No new performance target.

**Constraints**: Anonymization MUST NOT touch any Booking, clinical documentation, or the linked Patient Account (FR-005/FR-006). MUST be data-layer-guarded to the extent a "block while active future booking exists" precondition is a real check, not merely advisory.

**Scale/Scope**: Extends the existing `com.cms.patient.record` package with one new field, one new `BookingRepository` query, one new service, one new exception, one thin staff controller, one frontend admin action.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Test-First Development**: PASS. Tasks will include failing tests for: successful
  anonymization with no active future bookings; rejection with an active future booking present,
  no fields modified; a retry succeeding after the blocking booking is cancelled; idempotent
  no-op on a second attempt (timestamp unchanged); an unknown patient; the linked Patient Account
  and all bookings/clinical documentation left untouched.
- **II. Simplicity & YAGNI**: PASS. Only the two fields that actually exist on `Patient`
  (`name`/`phone`) are cleared — no speculative new fields invented to match the source
  material's illustrative list; reuses the established Operations-or-ClinicAdmin write-action
  gate rather than inventing a new authorization scheme.
- **III. Modular, Library-First Architecture**: PASS. Lives in `com.cms.patient.record`, which
  already owns `Patient` — service AND controller together, mirroring this session's dominant
  "controller lives beside its service" pattern (`com.cms.clinical`, `com.cms.waitlist`, etc.);
  reads `com.cms.booking.BookingRepository` directly for a synchronous precondition check (not an
  asynchronous cross-module effect, so no event is needed here) — mirrors `com.cms.clinical`'s
  own established pattern of reading Booking data directly for its own precondition checks.
- **IV. Data Privacy & Integrity by Design**: PASS — directly the point of this feature. The
  "active future booking" block is the actual DPDP-mandated guard against erasing a patient's
  identity while they still have a pending clinical obligation at this clinic; the durable
  `anonymizedAt` marker is exactly the kind of explicit, auditable state the constitution's own
  anonymization-lifecycle language calls for, laying the groundwork for 034's own precondition
  check.

No violations — Complexity Tracking table not needed.

**Post-Phase-1 re-check**: PASS, unchanged. Phase 1 design (data-model.md, contracts/,
quickstart.md) introduced nothing beyond what this gate already evaluated.

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/cms/patient/record/
│   ├── Patient.java                                    # extend: + anonymizedAt, + anonymize() mutator
│   ├── PatientNotFoundException.java                   # new (research.md R3 - not reused from com.cms.booking)
│   ├── PatientHasActiveFutureBookingException.java      # new
│   ├── PatientAnonymizationService.java                 # new
│   ├── StaffPatientAnonymizationController.java          # new (staff JWT, Operations-or-ClinicAdmin)
│   └── dto/
│       └── PatientAnonymizationResponse.java             # new
├── src/main/java/com/cms/booking/
│   └── BookingRepository.java                          # extend: + existsActiveFutureBookingForPatient
├── src/main/resources/db/migration/
│   └── V22__patient_anonymized_at.sql                   # new
├── src/main/java/com/cms/identity/account/
│   └── SecurityConfig.java                             # extend: + 1 new matcher
└── src/test/java/com/cms/patient/record/integration/    # new package
    └── (new test classes)

frontend/
├── src/features/patient-anonymization/
│   ├── api.ts
│   └── AnonymizePatientButton.tsx
└── tests/patient-anonymization/
```

**Structure Decision**: Everything this feature adds — entity extension, exceptions, service, and
controller — lives in `com.cms.patient.record`, which already owns `Patient`. This mirrors the
session's dominant "controller lives beside its service" placement (`com.cms.clinical`,
`com.cms.waitlist`), not `com.cms.identity.admin`'s own Super-Admin-Basic-Auth-only controllers
(`ClinicVerificationController`/`DoctorVerificationController`) — this feature's authorization is
the ordinary staff JWT chain (Operations-or-ClinicAdmin), a different mechanism entirely, so its
controller does not belong alongside those. No new `SecurityConfig` chain — extends the existing
staff `/api/v1/clinics/**` chain.

## Complexity Tracking

*No violations — this section is not applicable.*
