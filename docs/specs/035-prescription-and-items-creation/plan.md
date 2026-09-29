# Implementation Plan: Prescription + Items Creation

**Branch**: `035-prescription-and-items-creation` | **Date**: 2026-09-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/035-prescription-and-items-creation/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Let the doctor assigned to a booking's slot record a Prescription — one or more typed medication
Items — as a permanent, immutable record, with a booking able to carry any number of independent
Prescriptions (unlike 030's Consultation Notes, capped at one). Technical approach: extends 030's
existing `com.cms.clinical` module with `Prescription`/`PrescriptionItem` entities (no uniqueness
constraint on `booking_id`, unlike `consultation_note`), a shared
`TreatingDoctorAuthorizationService` extracted from 030's `ConsultationNoteService` (now that a
second real caller needs the identical booking-lookup + doctor-identity-trace logic — 030 is
refactored in place to use it too, a minimal, behavior-preserving change), and two thin staff
endpoints (create, get) — no update or delete endpoint exists anywhere, structurally.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript/React (frontend) — matches every prior feature this session.

**Primary Dependencies**: Spring Boot, Spring Data JPA, Spring Security (staff JWT chain); React + Tailwind CSS on the frontend.

**Storage**: PostgreSQL via a new Flyway migration (`V20`).

**Testing**: JUnit 5 + Spring Boot Test (`@WebMvcTest`/`@SpringBootTest` + Testcontainers) on the backend; Vitest/React Testing Library on the frontend — mirrors every prior feature.

**Target Platform**: Linux server (Spring Boot backend), browser (React frontend).

**Project Type**: Web application (`backend/` + `frontend/`, existing structure).

**Performance Goals**: No new performance target.

**Constraints**: No update or delete code path may exist for `Prescription`/`PrescriptionItem` anywhere (FR-002) — structural, not just a business rule. No uniqueness constraint on `Prescription.booking_id` (FR-003) — the opposite of 030's own constraint.

**Scale/Scope**: Extends the existing `com.cms.clinical` module with two new entities, one shared extracted authorization helper (also refactoring 030 in place), one service, one thin staff controller (create + get), one frontend form.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Test-First Development**: PASS. Tasks will include failing tests for: successful creation
  with multiple Items by the treating doctor; a second, independent Prescription succeeding for
  the same booking; rejection of a zero-Item submission; rejection by a non-treating doctor,
  ClinicAdmin, and Operations staff; successful retrieval including all Items; an unknown
  booking; and a regression check that 030's own existing behavior is unchanged after the shared
  authorization-helper refactor.
- **II. Simplicity & YAGNI**: PASS. Extracting the shared authorization helper now is justified by
  a genuine second caller (not speculative); no structured sub-fields beyond what's stated; no
  broader read access than the treating doctor's own; no clinic-staffing-status re-check beyond
  the doctor-identity trace the spec explicitly says is sufficient (FR-005).
- **III. Modular, Library-First Architecture**: PASS. Extends the existing `com.cms.clinical`
  module in place — no new module boundary; depends on `com.cms.booking`/`com.cms.identity` for
  reads only, unchanged from 030's own established dependency direction.
- **IV. Data Privacy & Integrity by Design**: PASS. Immutability is structural (no update/delete
  code exists). Prescription content is clinical content, explicitly in-scope for the eventual
  3-year-retention/anonymization lifecycle (a later feature's concern, not this one's) — nothing
  here conflicts with that. No concurrency-sensitive uniqueness invariant applies here (unlike
  030) since multiple Prescriptions per booking are explicitly allowed — no data-layer race to
  close for cardinality; Item-level correctness (at least one Item) is a request-shape validation,
  not a concurrency concern.

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
├── src/main/java/com/cms/clinical/
│   ├── TreatingDoctorAuthorizationService.java         # new: extracted shared helper
│   ├── ConsultationNoteService.java                    # extend (030): uses the extracted helper, no behavior change
│   ├── Prescription.java                               # new
│   ├── PrescriptionItem.java                            # new
│   ├── PrescriptionRepository.java                      # new
│   ├── PrescriptionService.java                          # new
│   ├── StaffPrescriptionController.java                  # new
│   └── dto/
│       ├── CreatePrescriptionRequest.java
│       ├── PrescriptionItemRequest.java
│       ├── PrescriptionResponse.java
│       └── PrescriptionItemResponse.java
├── src/main/resources/db/migration/
│   └── V20__create_prescription.sql                    # new
├── src/main/java/com/cms/identity/account/
│   └── SecurityConfig.java                             # extend: + 2 new matchers (create, get)
└── src/test/java/com/cms/clinical/integration/          # extend existing package
    └── (new test classes)

frontend/
├── src/features/prescriptions/
│   ├── api.ts
│   └── PrescriptionForm.tsx
└── tests/prescriptions/
```

**Structure Decision**: Extends 030's existing `com.cms.clinical` module in place — no new
module. Both endpoints extend the existing `com.cms.identity.account.SecurityConfig` staff chain,
mirroring 030 exactly — no new `SecurityConfig` chain, no patient-facing endpoint.

## Complexity Tracking

*No violations — this section is not applicable.*
