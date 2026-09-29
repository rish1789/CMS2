# Contract: Fixed-Time Session Slot Pre-Generation (service interfaces, not REST endpoints)

Per Constitution Principle III's explicit allowance — no HTTP endpoint exists yet; every eventual caller (016/017/018/023, and 022 for `BufferSlotCalculator`) is unbuilt.

## `SlotGenerationService.generateSlotsFor(Session session) -> List<Slot>`

Called only from `SessionGenerationService.generateForSchedule` for a newly-created Fixed-Time Session (data-model.md). Not intended to be called independently against an already-Slot-populated Session (no idempotency guard of its own — the caller's own "only call for a newly-created Session" discipline is what prevents duplicates; see research.md).

## `BufferSlotCalculator.calculateBufferSlotCount(Session session) -> int`

The seam 022 will implement against later. v1's only bean (`ColdStartBufferSlotCalculator`) always returns `1`.

## Contract Invariants (traced to spec)

- Every Slot generated for a Session satisfies `slotStart + interval <= session.endTime` — never extends past the window (FR-004).
- The number of `isBuffer = true` Slots for a Session always equals `bufferSlotCalculator.calculateBufferSlotCount(session)` at the moment of generation (FR-006).
- No `Slot` is ever created for a Session whose `mode != FIXED_TIME` (FR-003, SC-002).
- Every created `Slot`'s `status` is `OPEN` (FR-002, SC-001).
