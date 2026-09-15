# Implementation Plan: Queue/Token Session Slot-on-Booking Generation

**Branch**: `019-queue-slot-on-demand-generation` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/019-queue-slot-on-demand-generation/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Extends 012's `Slot` entity with a nullable `tokenNumber` (and makes `startTime`/`endTime` nullable, for the mode this feature's Slots don't have times for), and adds `QueueSlotService.issueNextSlot(UUID sessionId) -> Slot` — a directly-callable, no-automatic-trigger capability (018, its only real caller, is unbuilt) that creates one new Slot per call with the next never-reused token number for that Session. Concurrency is closed with the same non-transactional-outer-retry-loop / fresh-transaction-per-attempt pattern already proven in 011, appropriate here because concurrent same-Session bookings are a realistic scenario, not a rare edge case.

## Technical Context

**Language/Version**: Java 21 (backend only — no HTTP endpoint; spec Assumptions).

**Primary Dependencies**: None new — extends `com.cms.scheduling.Slot`/`SlotRepository`/`Session` (011/012) directly.

**Storage**: PostgreSQL — one new migration (`V11`): `slot.start_time`/`slot.end_time` become nullable, `slot.token_number` added, a partial unique index `(session_id, token_number) WHERE token_number IS NOT NULL`.

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers (backend only — extends 018's existing `AbstractSlotGenerationIntegrationTest` fixture).

**Target Platform**: Linux container (Docker).

**Project Type**: Extension of the existing `com.cms.scheduling` module — no new project structure.

**Performance Goals**: Same order of magnitude as prior features — one small read + one insert per call, with a bounded retry loop only on genuine contention.

**Constraints**:
- Token uniqueness within a Session MUST be closed at the database layer (a partial unique index), not merely by an application-level "read max, add one" check — that check alone cannot prevent two concurrent callers from computing the same next number (Constitution IV).
- A lost race MUST be retried transparently (a fresh attempt, re-reading the now-current max), not surfaced as a failure to the caller — mirroring 011's own already-proven fresh-transaction-per-attempt pattern, since (unlike a duplicate-creation race with one legitimate winner) both concurrent callers here have equally legitimate claims to *some* new, correctly-sequential token.
- This feature MUST NOT create any Slot except in direct, explicit response to a call to `issueNextSlot` (FR-008) — no scheduled job, no hook into 011/012's own generation path.
- `issueNextSlot` MUST reject a non-Queue/Token Session before attempting any write (FR-004).

**Scale/Scope**: Single feature — 1 migration (3 schema changes to the existing `slot` table), 1 new entity constructor overload, 1 new repository query, 1 new service, 2 new exceptions. 0 new endpoints, 0 new entities.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Test-First Development | PASS | Tests for every FR (sequential issuance, never-reused numbering, wrong-mode/not-found rejection, genuine concurrent-issuance race) written before implementation. |
| II. Simplicity & YAGNI | PASS | No Booking entity or trigger invented ahead of 018 (spec Assumptions); reuses 012's existing `Slot` entity rather than a parallel Queue-specific entity. |
| III. Modular, Library-First Architecture | PASS | Stays entirely inside `com.cms.scheduling`; service-interface-only contract (Principle III's explicit allowance) since no HTTP caller exists yet. |
| IV. Data Privacy & Integrity by Design | PASS | The never-reused-token guarantee is exactly a "close a duplicate/collision race at the data layer" requirement, closed with a DB constraint plus a graceful, proven retry pattern - not an application-only check. |

No violations — Complexity Tracking is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/019-queue-slot-on-demand-generation/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── queue-slot-issuance.md
└── tasks.md
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/cms/scheduling/
│   ├── Slot.java                              # extended: +tokenNumber, +queue-mode constructor
│   ├── SlotRepository.java                    # extended: +findMaxTokenNumberBySession_Id
│   ├── QueueSlotService.java                  # new
│   ├── SessionNotFoundException.java           # new
│   └── NotAQueueSessionException.java           # new
├── src/main/resources/db/migration/
│   └── V11__slot_queue_token_support.sql       # new
└── src/test/java/com/cms/scheduling/integration/
    ├── QueueSlotIssuanceOrderTest.java
    ├── QueueSlotIssuanceConcurrencyTest.java
    └── QueueSlotIssuanceRejectionTest.java
```

**Structure Decision**: Pure extension of the existing `com.cms.scheduling` module — no new module, no new endpoint.

## Post-Design Constitution Re-Check

Re-evaluated after Phase 1 (data model + contracts + quickstart, below): all four principles still PASS. The DB-constraint-plus-retry design (data-model.md) confirms Principle IV's data-layer-closure requirement holds in the detailed design.

## Complexity Tracking

*No violations — table intentionally left empty.*
