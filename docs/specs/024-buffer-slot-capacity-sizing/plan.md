# Implementation Plan: Buffer Slot Capacity Sizing

**Branch**: `024-buffer-slot-capacity-sizing` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/024-buffer-slot-capacity-sizing/spec.md`

## Summary

Supplies the real, data-driven `com.cms.scheduling.BufferSlotCalculator` implementation that
018's own seam has been waiting for since it was built — `SlotGenerationService` already calls
this interface and already distributes buffer slots evenly; nothing about that changes. The
new implementation computes a doctor's trailing-90-day no-show rate from 023's `Slot.status =
NO_SHOW` data (via 016's `Booking`), falls back to the existing `ColdStartBufferSlotCalculator`
(reused, not duplicated) below a 5-sample floor, and otherwise caps the rate-as-percentage at
20% of the session's slots and 3 slots absolute.

## Technical Context

**Language/Version**: Java 21 (backend only — no HTTP endpoint or frontend, per spec Assumptions).

**Primary Dependencies**: Spring Boot, Spring Data JPA.

**Storage**: No new migration — reads existing `slot`, `session`, `booking` tables.

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers (PostgreSQL 16-alpine), matching every prior feature this session.

**Target Platform**: Existing Spring Boot backend service.

**Project Type**: Backend-only. New implementation class lives in `com.cms.booking` (not
`com.cms.scheduling`, despite implementing a `com.cms.scheduling` interface — see research.md
for why).

**Performance Goals**: N/A beyond existing system norms — this runs once per session generated, already inside 015's existing nightly/manual generation flow.

**Constraints**: Must not change `SlotGenerationService`'s or `BufferSlotCalculator`'s existing
signatures — 018's seam is deliberately unchanged (build-order.md's own "upgrade in place"
framing). Must not introduce a circular package dependency between `com.cms.scheduling` and
`com.cms.booking`.

**Scale/Scope**: One new class (`RiskBasedBufferSlotCalculator`), one new repository query
method. No new entity, no new migration, no HTTP endpoint, no frontend.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Test-First Development**: PASS (planned) — tasks.md includes integration tests for the
  flat-default floor, the risk-based computation, both caps, the zero-rate case, and even
  distribution, written before implementation.
- **II. Simplicity & YAGNI**: PASS — reuses `ColdStartBufferSlotCalculator` rather than
  duplicating its "return 1" logic; reuses `SlotGenerationService`'s existing even-distribution
  mechanism entirely unchanged; no new entity or migration; no clinic-configurability surface
  (explicitly out of scope per spec).
- **III. Modular, Library-First Architecture**: PASS — the new implementation lives in
  `com.cms.booking` (which already depends on `com.cms.scheduling`) rather than reversing that
  established one-way dependency; it implements a `com.cms.scheduling`-owned interface, a
  standard cross-module extension-point pattern Spring's DI makes trivial without requiring the
  implementation to live in the interface's own package.
- **IV. Data Privacy & Integrity by Design**: PASS — this feature only reads existing data
  (`Slot`, `Booking`); it creates or mutates nothing, so there is no new race or identity
  concern to close.

No violations. Complexity Tracking section not needed.

## Project Structure

### Documentation (this feature)

```text
specs/024-buffer-slot-capacity-sizing/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── tasks.md
```

*(No `contracts/` — no HTTP endpoint, per Constitution III's service-interface-only allowance.)*

### Source Code (repository root)

```text
backend/
├── src/main/java/com/cms/booking/
│   ├── RiskBasedBufferSlotCalculator.java   # NEW - implements com.cms.scheduling.BufferSlotCalculator
│   └── BookingRepository.java               # EXTENDED - one new count-based query
└── src/test/java/com/cms/booking/integration/
    └── RiskBasedBufferSlotCalculatorTest.java   # NEW
```

**Structure Decision**: The new calculator lives in `com.cms.booking` (which already depends on
`com.cms.scheduling` for `Slot`/`Session`/`SlotStatus`), implementing the
`com.cms.scheduling.BufferSlotCalculator` interface without requiring any change to
`com.cms.scheduling` itself beyond what already exists — this keeps the module dependency
graph one-directional (research.md), and matches the existing precedent of `com.cms.booking`
already reaching into `com.cms.scheduling`'s repositories for reads.

## Complexity Tracking

*No Constitution Check violations — this section is not needed.*
