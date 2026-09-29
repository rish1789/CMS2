# Implementation Plan: Remove Reserved-Capacity Walk-In Slots

**Branch**: `058-remove-reserved-capacity` | **Date**: 2026-09-22 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/058-remove-reserved-capacity/spec.md`

## Summary

Delete the buffer-slot / reserved-capacity mechanism end to end: `Slot.isBuffer` and its column,
the `BufferSlotCalculator` seam and both implementations (018's flat default, 024's risk-based
one), the even-spacing buffer-index logic in slot generation, every `isBuffer` filter/guard/DTO
field across three modules, and the "Reserved capacity" Day Sheet UI. Walk-in insertion (025)
drops from a 3-tier to a 2-tier priority search (no-show-freed, then regular-with-override) — a
real behavior change to that feature's documented contract, not an incidental side effect.

## Technical Context

**Language/Version**: Java 21 (backend, Spring Boot 3.3), TypeScript 5 / React 19 (frontend, Vite)

**Primary Dependencies**: Spring Data JPA/Hibernate, Flyway — no new dependency of any kind (this
is a pure removal)

**Storage**: PostgreSQL — one new migration dropping `slot.is_buffer` (forward-only per this
project's Flyway convention; a mistaken migration is corrected by a new migration, never edited)

**Testing**: JUnit 5 + Mockito (unit), `@WebMvcTest` (contract), Testcontainers (integration,
written/compiled but unexecuted in this sandbox per the project's standing Docker limitation) on
the backend; Vitest + Testing Library on the frontend

**Target Platform**: Existing web app (Spring Boot backend on :8080, Vite/React frontend on :5173)
— no new platform

**Project Type**: Web application (existing `backend/` + `frontend/` structure)

**Performance Goals**: N/A — this reduces logic (no risk-based query, no buffer-index math), no
new performance surface

**Constraints**: Fixed-Time slots only (matches the existing scope of everything being removed);
Queue/Token-mode slots are entirely unaffected (`isBuffer` was already always `false`/unused
there); the No-Show sweep, the day-sheet Smart Status Flow (057), and consultation
notes/prescriptions are untouched — none of them read `isBuffer`

**Scale/Scope**: Touches `scheduling` (domain field, calculator seam, generation logic,
repository filters), `booking` (the risk-based calculator, two booking-service guards + one
exception class + its handler mapping, the day-sheet DTO, walk-in insertion's tier search), and
the frontend Day Sheet feature area. No new module; two files/classes are deleted outright
(`BufferSlotCalculator`'s seam becomes unnecessary once there is exactly one caller and zero
implementations — see research.md Decision 1 for why the interface itself is removed, not just
its implementations).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Test-First Development**: Every removal below has a corresponding test change (deletion of
  now-meaningless tests, or an update proving the new behavior) tracked task-by-task in
  `/speckit-tasks`. Integration tests are updated and compiled per this project's standing
  convention even though they can't execute in this sandbox. **PASS**.
- **II. Simplicity & YAGNI**: This is the textbook case for this principle — removing an entire
  calculator seam, its risk-scoring implementation, and every guard clause that existed only to
  protect a concept that no longer exists. Nothing new is added. **PASS**.
- **III. Modular, Library-First Architecture**: No module boundary changes. `RiskBasedBufferSlotCalculator` living in `com.cms.booking` (to reach `BookingRepository` without a cycle,
  per 024's original research.md) is deleted wholesale along with the interface it implemented —
  no boundary is left half-crossed. **PASS**.
- **IV. Data Privacy & Integrity by Design**: No patient-identifying data involved. The migration
  drops a single non-nullable boolean column with a default — no data-loss risk beyond the column
  itself (which carries no information anyone reads once this ships, per spec.md's Edge Cases).
  **PASS**.
- **Multi-tenancy**: No change to any clinic-scoping pattern — every touched query already scopes
  by clinic/session the same way its siblings do. **PASS**.
- **Out-of-scope boundaries**: No payments, uploads, notifications, or reschedule touched. **PASS**.
- **Business-rule change note** (constitution's Development Workflow bullet on scheduling/booking/
  waitlist changes needing a rationale): this changes a documented business rule — walk-in
  insertion's (025) priority search narrows from 3 tiers to 2. The rationale is spec.md's own: the
  concept being prioritized (reserved capacity) no longer exists, so its tier is removed, not
  reinterpreted. Recorded here per the constitution's own requirement, not left implicit.

No violations to justify — Complexity Tracking table is intentionally omitted below.

**Post-Design Re-check** (after Phase 0/1 artifacts below): still **PASS** on every principle. No
design decision introduced any new complexity; every Phase 0 decision is "delete this, and here is
exactly what code becomes newly-unreachable as a result" — confirmed against the full codebase
inventory in research.md.

## Project Structure

### Documentation (this feature)

```text
specs/058-remove-reserved-capacity/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md         # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit-tasks — not created here)
```

### Source Code (repository root)

```text
backend/src/main/java/com/cms/
├── scheduling/
│   ├── domain/
│   │   └── Slot.java                              # - isBuffer field/column, both constructors simplified
│   ├── service/
│   │   ├── SlotGenerationService.java              # - bufferSlotCalculator dependency, computeEvenlySpacedIndices, buffer-index wiring
│   │   ├── BufferSlotCalculator.java               # DELETED — interface has zero callers once above lands
│   │   └── ColdStartBufferSlotCalculator.java      # DELETED — 018's flat-default implementation
│   └── repository/
│       └── SlotRepository.java                     # - "AND s.isBuffer = false" from findOpenFixedTimeSlots + findOpenFixedTimeSlotsOnDate (both value and countQuery)
└── booking/
    ├── service/
    │   ├── RiskBasedBufferSlotCalculator.java      # DELETED — 024's risk-scoring implementation
    │   ├── PatientBookingService.java              # - the isBuffer guard + BufferSlotNotDirectlyBookableException throw
    │   ├── StaffBookingService.java                # - the isBuffer guard + BufferSlotNotDirectlyBookableException throw
    │   └── WalkInInsertionService.java             # selectTier: drop the buffer tier, 3-tier search becomes 2-tier
    ├── exception/
    │   ├── BufferSlotNotDirectlyBookableException.java  # DELETED — zero throw sites remain
    │   └── BookingExceptionHandler.java            # - the now-dead @ExceptionHandler mapping for it
    └── dto/
        └── SessionDaySheetResponse.java             # - isBuffer field and its mapping

backend/src/main/resources/db/migration/
└── V35__drop_slot_is_buffer.sql                     # NEW — ALTER TABLE slot DROP COLUMN is_buffer

frontend/src/features/day-sheet/
├── api.ts                                            # - isBuffer from the Slot type
└── SessionSlotsView.tsx                              # - "Reserved capacity" label, "No direct booking..." text, the isBuffer branch suppressing Book
```

**Structure Decision**: Existing `backend/` + `frontend/` web-application layout, unchanged. This
is a removal within the two modules that already own the relevant domain objects (`scheduling`
for the slot field/generation, `booking` for the calculator/guards/walk-in tier search) — no new
module, no relocation of anything that survives.

## Complexity Tracking

*No Constitution Check violations — table intentionally omitted.*
