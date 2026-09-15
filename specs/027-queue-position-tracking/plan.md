# Implementation Plan: Queue Position Tracking (Queue-Mode Only)

**Branch**: `027-queue-position-tracking` | **Date**: 2026-09-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/027-queue-position-tracking/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Give staff and patients a way to see a queue-mode Booking's current position — the count of
active (still-`BOOKED`) Bookings with a lower token number in the same Session, plus one —
computed fresh on every query (deliberately not stored/cached, unlike 023's delay figure).
Technical approach: one new `QueuePositionService` in the existing `com.cms.booking` module,
exposed via two thin, separately-secured controllers (staff, clinic-scoped; patient,
ownership-scoped) that both call the identical computation (research.md).

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript/React (frontend) — matches every prior feature this session.

**Primary Dependencies**: Spring Boot, Spring Data JPA, Spring Security (staff JWT chain + patient JWT chain); React + Tailwind CSS on the frontend.

**Storage**: PostgreSQL — no schema change this feature (pure read).

**Testing**: JUnit 5 + Spring Boot Test (`@WebMvcTest`/`@SpringBootTest` + Testcontainers) on the backend; Vitest/React Testing Library on the frontend — mirrors every prior feature.

**Target Platform**: Linux server (Spring Boot backend), browser (React frontend).

**Project Type**: Web application (`backend/` + `frontend/`, existing structure).

**Performance Goals**: No new performance target — a single Session's Slot list (bounded by one clinic-day's queue), evaluated in-memory, reusing the existing indexed `findBySession_Id` query.

**Constraints**: The computation must never read from or write to a stored value — every call recomputes from current `Slot` state (FR-003).

**Scale/Scope**: One new service class, two new (thin) controllers, one new repository query, one new frontend component shared by both audiences' pages. No new entity, no migration.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Test-First Development**: PASS. Tasks will include failing tests (unit + Testcontainers
  integration, mirroring 025/026's precedent) for the position formula, both "not applicable"
  cases, and both endpoints' authorization/ownership scoping, written before the corresponding
  implementation.
- **II. Simplicity & YAGNI**: PASS. No stored/cached figure or trigger call sites where live
  computation suffices (research.md R2); no placeholder `CANCELLED` status added ahead of any
  feature that needs it (R3); a new role-agnostic repository query added only because this is
  genuinely the first staff-gated read with no role exclusion at all (R5), not a speculative
  generalization.
- **III. Modular, Library-First Architecture**: PASS. Lives entirely inside the existing
  `com.cms.booking` module, exposes two clear REST contracts over one shared internal service
  (never two divergent computations), reuses `SlotRepository`/`SessionRepository`/
  `RoleAssignmentRepository` through their existing public interfaces.
- **IV. Data Privacy & Integrity by Design**: PASS. No patient-identifying data newly exposed
  beyond confirming a Booking's own position; the patient endpoint's ownership check
  (`Patient.patientAccount` match) is the actual access boundary, structurally enforced in the
  query itself, not merely an application-level `if` a caller could bypass by omission.

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
├── src/main/java/com/cms/booking/
│   ├── QueuePositionService.java          # new
│   ├── StaffQueuePositionController.java  # new
│   ├── PatientQueuePositionController.java # new
│   ├── BookingNotFoundException.java      # new
│   └── dto/QueuePositionResponse.java     # new
├── src/main/java/com/cms/identity/account/
│   ├── RoleAssignmentRepository.java      # extend: + existsByAccount_IdAndClinic_IdAndActiveTrue
│   └── SecurityConfig.java                # extend: + 1 new matcher
├── src/main/java/com/cms/patient/account/
│   └── SecurityConfig.java                # extend: + 1 new matcher
└── src/test/java/com/cms/booking/integration/
    └── (new test classes)

frontend/
├── src/features/queue-position/
│   ├── api.ts                             # new
│   └── QueuePositionIndicator.tsx         # new
└── tests/queue-position/                  # new
```

**Structure Decision**: Existing web-application structure (`backend/` + `frontend/`). Backend
work lives entirely in the existing `com.cms.booking` module (015/016/017/018/025's home), with
matcher additions to both existing staff and patient `SecurityConfig` chains; frontend work is a
new `queue-position` feature directory, shared by both the staff and patient pages that use it.

## Complexity Tracking

*No violations — this section is not applicable.*
