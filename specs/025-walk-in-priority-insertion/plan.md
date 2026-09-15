# Implementation Plan: Walk-In / Priority Insertion

**Branch**: `025-walk-in-priority-insertion` | **Date**: 2026-09-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/025-walk-in-priority-insertion/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Give front-desk staff one endpoint to insert a walk-in patient into a running Fixed-Time Session,
where the system — not the staff member — selects the target Slot via a strict, three-tier
fallback: an OPEN buffer Slot, else a Slot freed by an earlier no-show, else any other OPEN
regular Slot gated behind a required written override reason. Technical approach: a new
`WalkInInsertionService` in the existing `com.cms.booking` module that reuses 015's fee resolution,
016's walk-in Patient creation and `saveAndFlush` race-closure pattern, and 021/022's Slot
`status`/`isBuffer` fields as-is — the only new schema is one nullable `overrideReason` column on
the existing `Booking` entity (research.md).

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript/React (frontend) — matches every prior feature this session.

**Primary Dependencies**: Spring Boot, Spring Data JPA, Spring Security (staff JWT chain); React + Tailwind CSS on the frontend.

**Storage**: PostgreSQL via Flyway-versioned migrations.

**Testing**: JUnit 5 + Spring Boot Test (`@WebMvcTest`/`@SpringBootTest` + Testcontainers) on the backend; Vitest/React Testing Library on the frontend — mirrors every prior feature.

**Target Platform**: Linux server (Spring Boot backend), browser (React frontend).

**Project Type**: Web application (`backend/` + `frontend/`, existing structure).

**Performance Goals**: No new performance target beyond existing booking endpoints — a single Session's Slot list (bounded by one clinic-day's schedule) evaluated in-memory, one existing indexed query (`findBySession_Id`).

**Constraints**: Must not weaken 016's existing one-Booking-per-Slot race guarantee; a failed insertion attempt must leave the original no-show Booking untouched (FR-001a).

**Scale/Scope**: One new service class, one new controller endpoint, one new Flyway migration (nullable column), one new frontend form. No new entity.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Test-First Development**: PASS. Tasks will include failing tests (unit + Testcontainers
  integration, mirroring 016/020/022's precedent) for the priority search, the FR-001a atomic
  delete-and-replace, the override-reason gate, and the concurrency race, written before the
  corresponding implementation.
- **II. Simplicity & YAGNI**: PASS. No new entity, no new abstraction layer; `resolveOrCreatePatient`
  is deliberately duplicated (not extracted) per this codebase's own established precedent
  (research.md R4) rather than adding speculative shared infrastructure.
- **III. Modular, Library-First Architecture**: PASS. Lives entirely inside the existing
  `com.cms.booking` module (the same module 015/016/017/018/022 already occupy), exposes one clear
  REST contract, reuses `FeeResolutionService` and `SlotRepository`/`BookingRepository` through
  their existing public interfaces — no reach-through into another module's internals.
- **IV. Data Privacy & Integrity by Design**: PASS. No new patient-identifying field beyond what
  016 already collects (name/phone, validated). The FR-001a delete-then-replace is the one
  integrity-sensitive operation in this feature; research.md R2/R3 documents exactly why placing it
  last (after every other precondition succeeds) satisfies the "no destructive write without a
  successful replacement" requirement, and the existing `uq_booking_slot` constraint remains the
  actual race-closure guarantee (unchanged, just reused).

No violations — Complexity Tracking table not needed.

**Post-Phase-1 re-check**: PASS, unchanged. Phase 1 design (data-model.md, contracts/,
quickstart.md) introduced nothing beyond what this gate already evaluated — one nullable column,
one service, one endpoint, all inside the existing `com.cms.booking` module boundary.

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
│   ├── Booking.java                       # extend: + overrideReason field
│   ├── BookingRepository.java             # unchanged (findBySlot_Id already exists)
│   ├── WalkInInsertionService.java        # new
│   ├── WalkInInsertionController.java     # new
│   ├── NoSlotAvailableException.java      # new
│   ├── OverrideReasonRequiredException.java # new
│   └── (BookingExceptionHandler.java)     # extend: + 2 new mappings
├── src/main/resources/db/migration/
│   └── V14__booking_override_reason.sql   # new
└── src/test/java/com/cms/booking/
    ├── WalkInInsertionServiceTest.java    # new (unit)
    └── WalkInInsertionIntegrationTest.java # new (Testcontainers)

frontend/
├── src/features/staff-booking/
│   ├── WalkInForm.tsx                     # new
│   └── api.ts                             # extend: + insertWalkIn call
└── src/features/staff-booking/__tests__/
    └── WalkInForm.test.tsx                # new
```

**Structure Decision**: Existing web-application structure (`backend/` + `frontend/`). This feature
adds entirely to the already-established `com.cms.booking` module (015/016/017/018/022's home) and
`frontend/src/features/staff-booking/` (020's home) — no new top-level module or directory.

## Complexity Tracking

*No violations — this section is not applicable.*
