# Implementation Plan: External Record Reference

**Branch**: `036-external-record-reference` | **Date**: 2026-09-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/036-external-record-reference/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Let the doctor assigned to a booking's slot record a typed-summary External Record Reference —
never a file upload — with a booking able to carry any number of independent references (same
cardinality as 031's Prescriptions, unlike 030's Consultation Notes). Technical approach: extends
030/031's existing `com.cms.clinical` module with a single flat `ExternalRecordReference` entity
(no child collection, unlike 031), reuses the already-shared `TreatingDoctorAuthorizationService`
as its third caller, and two thin staff endpoints (create, list) — no update or delete endpoint
exists anywhere, structurally, completing the constitution's own named clinical-documentation
module trio.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript/React (frontend) — matches every prior feature this session.

**Primary Dependencies**: Spring Boot, Spring Data JPA, Spring Security (staff JWT chain); React + Tailwind CSS on the frontend.

**Storage**: PostgreSQL via a new Flyway migration (`V21`).

**Testing**: JUnit 5 + Spring Boot Test (`@WebMvcTest`/`@SpringBootTest` + Testcontainers) on the backend; Vitest/React Testing Library on the frontend.

**Target Platform**: Linux server (Spring Boot backend), browser (React frontend).

**Project Type**: Web application (`backend/` + `frontend/`, existing structure).

**Performance Goals**: No new performance target.

**Constraints**: No update or delete code path may exist for `ExternalRecordReference` anywhere (FR-002). No file/document attachment field may exist anywhere on it (FR-006) — a structural, not merely a validation, constraint. No uniqueness constraint on `booking_id` (FR-003).

**Scale/Scope**: Extends the existing `com.cms.clinical` module with one new flat entity, one service (reusing the already-shared authorization helper), one thin staff controller (create + list), one frontend form.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Test-First Development**: PASS. Tasks will include failing tests for: successful creation
  by the treating doctor; a second, independent reference succeeding for the same booking;
  rejection by a non-treating doctor, ClinicAdmin, and Operations staff; successful retrieval; an
  unknown booking; no status precondition; and a regression check that 030/031's own existing
  behavior is unchanged (no code in either was touched by this feature, but the shared
  authorization service now has a third caller).
- **II. Simplicity & YAGNI**: PASS. No child/line-item collection (unlike 031) since the source
  material describes flat scalar fields only; no file-attachment field of any kind (explicitly,
  system-wide, out of scope); no broader read access than the treating doctor's own; reuses the
  already-extracted shared authorization service rather than a fourth reimplementation.
- **III. Modular, Library-First Architecture**: PASS. Extends the existing `com.cms.clinical`
  module in place — no new module boundary, completing the constitution's own named trio; depends
  on `com.cms.booking`/`com.cms.identity` for reads only, unchanged dependency direction.
- **IV. Data Privacy & Integrity by Design**: PASS. Immutability is structural (no update/delete
  code exists). Explicitly typed-summary-only content (never a file) directly serves DPDP's own
  spirit (no unbounded binary clinical data to manage the lifecycle of). Clinical content,
  explicitly in-scope for the eventual 3-year-retention/anonymization lifecycle (a later
  feature's concern). No concurrency-sensitive invariant applies (multiple references per booking
  are explicitly allowed, mirrors 031's own identical reasoning).

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
│   ├── ExternalRecordReference.java                    # new
│   ├── ExternalRecordReferenceRepository.java           # new
│   ├── ExternalRecordReferenceService.java               # new (reuses TreatingDoctorAuthorizationService, no changes to it)
│   ├── StaffExternalRecordReferenceController.java       # new
│   └── dto/
│       ├── CreateExternalRecordReferenceRequest.java
│       └── ExternalRecordReferenceResponse.java
├── src/main/resources/db/migration/
│   └── V21__create_external_record_reference.sql       # new
├── src/main/java/com/cms/identity/account/
│   └── SecurityConfig.java                             # extend: + 2 new matchers (create, list)
└── src/test/java/com/cms/clinical/integration/          # extend existing package
    └── (new test classes)

frontend/
├── src/features/external-record-references/
│   ├── api.ts
│   └── ExternalRecordReferenceForm.tsx
└── tests/external-record-references/
```

**Structure Decision**: Extends 030/031's existing `com.cms.clinical` module in place — no new
module, no new `SecurityConfig` chain. Reuses `TreatingDoctorAuthorizationService` (031's
extraction) unchanged — this feature makes zero edits to any 030/031 file.

## Complexity Tracking

*No violations — this section is not applicable.*
