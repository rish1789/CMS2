# Implementation Plan: Automatic No-Show Detection

**Branch**: `023-no-show-detection` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/023-no-show-detection/spec.md`

## Summary

A periodic background sweep (mirroring 015's existing `NightlySessionGenerationTrigger` /
`@Scheduled` pattern) that finds every Fixed-Time `Slot` still `BOOKED` more than 10 minutes
past its scheduled time, not on hold, and flips it to a new `NO_SHOW` status — extending
`SlotStatus` in place exactly as 020 extended it with `BOOKED`. Introduces a currently-unset
`onHold` boolean on `Slot` per Clarifications (infrastructure a stated business rule requires,
ahead of the not-yet-built feature that will actually set it). No HTTP endpoint — this is a
fully internal, automatic process (Constitution III's service-interface-only allowance, the
same shape 015's `FeeResolutionService` used before 016 gave it a real caller).

## Technical Context

**Language/Version**: Java 21 (backend only — no frontend surface for this feature, per spec Assumptions).

**Primary Dependencies**: Spring Boot, Spring's `@Scheduled` (already enabled app-wide via `SessionGenerationSchedulingConfig`), Spring Data JPA.

**Storage**: PostgreSQL via a new Flyway migration adding `slot.on_hold` (boolean, default false); reuses existing `slot`, `session`, `booking` tables otherwise.

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL 16-alpine) — matches every prior feature this session. No MockMvc (no HTTP surface).

**Target Platform**: Existing Spring Boot backend service.

**Project Type**: Backend-only extension of the existing `com.cms.scheduling` module.

**Performance Goals**: Sweep runs frequently enough that "within one sweep cycle" (SC-001) stays a short, predictable latency — every 1 minute (implementation-level default, not a spec requirement; no acceptance criterion depends on the exact interval).

**Constraints**: Must not reintroduce the self-invocation `@Transactional` bypass this session found (twice) in 020's original code and in 022's first implementation attempt — the sweep's per-Slot update must not rely on a same-class self-invoked `@Transactional` helper.

**Scale/Scope**: One new migration, one enum value, one new boolean column, one new service, one new scheduled trigger. No new entity, no HTTP endpoint, no frontend.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Test-First Development**: PASS (planned) — tasks.md includes integration tests for the
  grace-period boundary, hold-exemption, no-reprocessing, and Queue-mode exclusion, written
  before implementation.
- **II. Simplicity & YAGNI**: PASS — no new entity; `SlotStatus` extended in place rather than
  a new `Booking`-level status; no denormalized no-show counter on `Patient` (a trailing window
  needs per-event timestamps a counter can't provide, and nothing yet consumes an aggregate
  anyway); no query method built for FR-005's "queryable" requirement beyond what the extended
  `SlotStatus` + existing `Booking.patient`/`Booking.slot` associations already provide
  structurally, since no caller exists yet (015 `FeeResolutionService` precedent); no manual
  re-run endpoint (not required, spec Assumptions).
- **III. Modular, Library-First Architecture**: PASS — lives entirely in `com.cms.scheduling`
  (the module that already owns `Slot`/`SlotStatus`/`Session`), a clean service-interface-only
  capability with no HTTP endpoint (Constitution III's explicit allowance), matching 015's own
  precedent exactly.
- **IV. Data Privacy & Integrity by Design**: PASS — no identity-record creation, no
  concurrency-sensitive race to close (each Slot transitions BOOKED→NO_SHOW exactly once,
  filtered by its own current status at query time within a single sweep run; a held Slot is
  structurally excluded by the same query, not a race-prone secondary check).

No violations. Complexity Tracking section not needed.

## Project Structure

### Documentation (this feature)

```text
specs/023-no-show-detection/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── tasks.md
```

*(No `contracts/` — this feature has no HTTP endpoint, per Constitution III's service-interface-
only allowance; see research.md.)*

### Source Code (repository root)

```text
backend/
├── src/main/resources/db/migration/
│   └── V13__slot_no_show_and_hold_support.sql   # NEW
├── src/main/java/com/cms/scheduling/
│   ├── SlotStatus.java              # EXTENDED - adds NO_SHOW
│   ├── Slot.java                    # EXTENDED - adds onHold (+ getter/setter)
│   ├── SlotRepository.java          # EXTENDED - one new candidate-lookup query
│   ├── NoShowDetectionService.java  # NEW
│   └── NoShowDetectionTrigger.java  # NEW - @Scheduled, mirrors NightlySessionGenerationTrigger
└── src/test/java/com/cms/scheduling/integration/
    └── NoShowDetectionTest.java     # NEW
```

**Structure Decision**: Extends the existing `com.cms.scheduling` module along its own
established boundary — `Slot`/`SlotStatus`/`Session` already live there, and this feature adds
no data or behavior belonging to any other module. It never touches `com.cms.booking` at all:
FR-005's "queryable per-patient" requirement is satisfied structurally by `Slot.status =
NO_SHOW` plus `Booking`'s already-existing `slot`/`patient` associations — a future feature
that needs the actual join query builds it in its own module when it needs it, not this one.

## Complexity Tracking

*No Constitution Check violations — this section is not needed.*
