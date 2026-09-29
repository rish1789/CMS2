# Implementation Plan: Fixed-Time Session Slot Pre-Generation

**Branch**: `018-fixed-time-slot-pregeneration` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/018-fixed-time-slot-pregeneration/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Defines `Slot` (first time, extends 011/013's `com.cms.scheduling` module) and `SlotGenerationService.generateSlotsFor(Session)`, wired directly into 011's existing `SessionGenerationService.generateForSchedule()` immediately after a newly-created Fixed-Time Session is saved — in the same transaction, per the source business rule. Regular slots are computed from the Session's own snapshotted time window and interval; buffer-slot count comes from a new `BufferSlotCalculator` interface whose only v1 implementation (`ColdStartBufferSlotCalculator`) always returns `1`, per build-order.md's documented 012↔022 resolution — a seam 022 will later implement against instead of touching this feature's code. Queue/Token Sessions are untouched (no call into this feature's code at all).

## Technical Context

**Language/Version**: Java 21 (backend only — no HTTP endpoint or UI; spec Assumptions).

**Primary Dependencies**: None new — extends `com.cms.scheduling.Session`/`SessionGenerationService`/`ScheduleMode` (011/013) directly.

**Storage**: PostgreSQL — one new migration (`V10`): `slot` table, FK to `session`.

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers (backend only — extends 015's existing `AbstractSessionGenerationIntegrationTest` fixture).

**Target Platform**: Linux container (Docker).

**Project Type**: Extension of the existing `com.cms.scheduling` module — no new project structure.

**Performance Goals**: Same order of magnitude as 011's own generation — one small, bounded loop (a day's worth of slots, typically well under 100) per newly-created Fixed-Time Session.

**Constraints**:
- Slot generation for a Session MUST happen inside the same per-schedule transaction 011's `generateForSchedule` already uses — no separate pass, no eventual-consistency window where a Fixed-Time Session exists with zero Slots (FR-001).
- `BufferSlotCalculator` MUST be a distinct, Spring-injected component — `SlotGenerationService` MUST depend on the interface, never inline the cold-start `1` literal itself, so 022 can supply a different implementation with zero changes to slot-creation or even-distribution logic (FR-004, spec Assumptions).
- No Slot MUST extend past the Session's own recorded end time (FR-004) — a trailing partial interval that doesn't fully fit is simply not generated.
- No Slot of any kind is created for a Queue/Token Session by this feature (FR-003) — the hook into `generateForSchedule` MUST be conditioned on `session.getMode() == FIXED_TIME`.

**Scale/Scope**: Single feature — 1 new entity (`Slot`), 1 new enum (`SlotStatus`), 1 migration, 1 repository, 1 new interface + 1 implementation (`BufferSlotCalculator`/`ColdStartBufferSlotCalculator`), 1 new service (`SlotGenerationService`), 1 small hook added to `SessionGenerationService`. 0 new endpoints.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Test-First Development | PASS | Tests for every FR (correct slot count/spacing, OPEN status, zero slots for Queue/Token, buffer count and even placement, no duplicate generation on repeat) written before implementation. |
| II. Simplicity & YAGNI | PASS | `SlotStatus` defined with only its one currently-meaningful value; no Booking/BOOKED state invented ahead of the features that need it. The `BufferSlotCalculator` seam is not speculative gold-plating — build-order.md explicitly documents that 022 will need to replace this exact logic, so building the seam now (not the formula) is the minimal correct response to an already-known future need. |
| III. Modular, Library-First Architecture | PASS | Stays entirely inside `com.cms.scheduling`; extends 011's existing generation transaction rather than introducing new cross-module coupling. |
| IV. Data Privacy & Integrity by Design | PASS | Not identity/patient data. Slot generation happening inside 011's existing per-schedule transaction is itself an integrity guarantee (no Fixed-Time Session can ever be observed mid-generation with a partial or absent Slot set). |

No violations — Complexity Tracking is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/018-fixed-time-slot-pregeneration/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── slot-generation.md
└── tasks.md
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/cms/scheduling/
│   ├── Slot.java                              # new entity
│   ├── SlotStatus.java                         # new enum (OPEN)
│   ├── SlotRepository.java                     # new
│   ├── BufferSlotCalculator.java                # new interface
│   ├── ColdStartBufferSlotCalculator.java       # new — v1's only implementation
│   ├── SlotGenerationService.java               # new
│   └── SessionGenerationService.java            # extended: +hook into generateForSchedule
├── src/main/resources/db/migration/
│   └── V10__create_slot.sql                     # new
└── src/test/java/com/cms/scheduling/integration/
    ├── SlotPreGenerationFixedTimeTest.java
    ├── SlotPreGenerationQueueModeTest.java
    ├── SlotPreGenerationBufferPlacementTest.java
    └── SlotPreGenerationNoDuplicateOnRepeatTest.java
```

**Structure Decision**: Pure extension of the existing `com.cms.scheduling` module (009/011/013/014/015) — no new module, no new frontend, no new endpoint.

## Post-Design Constitution Re-Check

Re-evaluated after Phase 1 (data model + contracts + quickstart, below): all four principles still PASS. The in-transaction hook design (data-model.md) confirms Principle IV's "no partial state" guarantee holds in the detailed design.

## Complexity Tracking

*No violations — table intentionally left empty.*
