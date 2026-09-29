# Quickstart: Fixed-Time Session Slot Pre-Generation

See [data-model.md](./data-model.md) and [contracts/slot-generation.md](./contracts/slot-generation.md). No HTTP endpoint — exercised via `SessionGenerationService.generate()` (015) and direct `SlotRepository` queries.

## Scenario 1 — Fixed-Time Session gets all its Slots, OPEN

1. Create a Fixed-Time Schedule (9:00–13:00, 15-minute slots) for a doctor at a clinic.
2. Call `SessionGenerationService.generate(runDate)`.
3. Query `SlotRepository.findBySession_Id(sessionId)` for one of the generated Sessions. **Expect**: 16 Slots, each `status = OPEN`, correctly spaced 15 minutes apart from 9:00 to 13:00.

## Scenario 2 — Queue/Token Session gets zero Slots

1. Create a Queue/Token Schedule. Generate.
2. Query Slots for that Session. **Expect**: empty — this feature never creates Slots for Queue/Token mode.

## Scenario 3 — Exactly 1 buffer Slot, placed near the middle

1. Using Scenario 1's 16-Slot Session, inspect which Slot has `isBuffer = true`. **Expect**: exactly one, at index 8 (the middle of 16).

## Scenario 4 — No duplicate Slots on repeated generation

1. Call `generate()` again for the same run date. **Expect**: the same Session (no duplicate per 011's own guarantee) and no additional Slots for it.
