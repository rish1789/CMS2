# Implementation Plan: Consultation Note Creation

**Branch**: `034-consultation-note-creation` | **Date**: 2026-09-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/034-consultation-note-creation/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Let the doctor assigned to a booking's slot write exactly one permanent, immutable consultation
note for it, and read it back afterward — the first feature to materialize the constitution's own
named "clinical documentation" module. Technical approach: a new `com.cms.clinical` module with a
`ConsultationNote` entity carrying a data-layer uniqueness constraint on its Booking (the actual
one-note-per-booking guarantee, not merely an application-level check), a single service enforcing
doctor-identity authorization by tracing `Booking → Slot → Session → DoctorProfile`, and two thin
staff endpoints (create, get) — no update or delete endpoint exists anywhere, structurally, not
just by convention.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript/React (frontend) — matches every prior feature this session.

**Primary Dependencies**: Spring Boot, Spring Data JPA, Spring Security (staff JWT chain); React + Tailwind CSS on the frontend.

**Storage**: PostgreSQL via a new Flyway migration (`V19`) — this feature's first new table.

**Testing**: JUnit 5 + Spring Boot Test (`@WebMvcTest`/`@SpringBootTest` + Testcontainers) on the backend; Vitest/React Testing Library on the frontend — mirrors every prior feature.

**Target Platform**: Linux server (Spring Boot backend), browser (React frontend).

**Project Type**: Web application (`backend/` + `frontend/`, existing structure).

**Performance Goals**: No new performance target.

**Constraints**: No update or delete code path may exist for `ConsultationNote` anywhere (FR-002) — this is a structural constraint on the implementation itself, not just a business rule to enforce at runtime.

**Scale/Scope**: One new module (`com.cms.clinical`), one new entity + migration, one shared service, one thin staff controller (create + get), one frontend form.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Test-First Development**: PASS. Tasks will include failing tests for: successful creation
  by the treating doctor; rejection of a second note for the same booking (including a genuine
  concurrent-creation race); rejection by a non-treating doctor and by a ClinicAdmin; successful
  retrieval by the treating doctor; an unknown booking.
- **II. Simplicity & YAGNI**: PASS. Single free-text content field, no structured sub-fields
  (spec Assumptions); no broader read access than the treating doctor's own (explicitly deferred,
  not built speculatively); no clinic-staffing-status re-check beyond the doctor-identity trace
  the spec explicitly says is sufficient (FR-005).
- **III. Modular, Library-First Architecture**: PASS. Materializes the constitution's own named
  "clinical documentation" module for the first time; depends on `com.cms.booking`/
  `com.cms.scheduling`/`com.cms.identity` for reads only (Booking, Slot, Session, DoctorProfile),
  never reaching into their internals beyond existing public entities/repositories — no
  cross-module write, no event needed (this feature has nothing downstream to notify).
- **IV. Data Privacy & Integrity by Design**: PASS — directly the point of this feature.
  Immutability is structural (no update/delete code exists, not merely unexposed via HTTP);
  one-note-per-booking is a data-layer uniqueness constraint, not an application-level check
  alone (Constitution IV's own explicit race-closure requirement). Clinical content, so this
  feature's data is explicitly in-scope for the eventual 3-year-retention/anonymization lifecycle
  (033/034/035's own future concern, not this feature's) — nothing here conflicts with that.

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
├── src/main/java/com/cms/clinical/                     # new module
│   ├── ConsultationNote.java
│   ├── ConsultationNoteRepository.java
│   ├── ConsultationNoteService.java
│   ├── ConsultationNoteAlreadyExistsException.java
│   ├── ConsultationNoteNotFoundException.java
│   ├── ClinicalDocumentationExceptionHandler.java
│   ├── StaffConsultationNoteController.java
│   └── dto/
│       ├── CreateConsultationNoteRequest.java
│       └── ConsultationNoteResponse.java
├── src/main/resources/db/migration/
│   └── V19__create_consultation_note.sql               # new
├── src/main/java/com/cms/identity/account/
│   └── SecurityConfig.java                             # extend: + 2 new matchers (create, get)
└── src/test/java/com/cms/clinical/integration/          # new package
    └── (new test classes)

frontend/
├── src/features/consultation-notes/
│   ├── api.ts
│   └── ConsultationNoteForm.tsx
└── tests/consultation-notes/
```

**Structure Decision**: A new top-level module, `com.cms.clinical` — the constitution's own named
module boundary materializing for the first time, alongside `com.cms.scheduling`/
`com.cms.booking`/`com.cms.waitlist`/`com.cms.notification`/`com.cms.discovery`. Staff-only (a
doctor is staff), so both endpoints extend the existing `com.cms.identity.account.SecurityConfig`
chain — no new `SecurityConfig` chain, no patient-facing endpoint at all.

## Complexity Tracking

*No violations — this section is not applicable.*
