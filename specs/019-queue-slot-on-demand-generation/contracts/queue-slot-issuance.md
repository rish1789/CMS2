# Contract: Queue/Token Session Slot-on-Booking Generation (service interface, not a REST endpoint)

Per Constitution Principle III's explicit allowance — no HTTP endpoint exists yet; 018 is this feature's only eventual caller.

## `QueueSlotService.issueNextSlot(UUID sessionId) -> Slot`

### Exceptions

| Exception | Condition |
|---|---|
| `SessionNotFoundException` | No `Session` with `sessionId` |
| `NotAQueueSessionException` | The Session's `mode` is not `QUEUE` |

### Guarantees

- Creates exactly one new `Slot`, with `tokenNumber` equal to one more than the highest ever issued for that Session (or `1` if none has been), and returns it.
- Never creates a `Slot` for a Fixed-Time Session, and never creates more or fewer than one `Slot` per successful call.
- Under concurrent calls for the same Session, every call succeeds with a distinct, correctly-sequential `tokenNumber` — no duplicates, no gaps.

## Contract Invariants (traced to spec)

- Two `Slot`s for the same `Session` never share a `tokenNumber` (FR-003, SC-002) — enforced at the database layer, not merely by this service's own read-then-write logic.
- A call naming a non-Queue/Token Session, or an unknown Session, never creates any `Slot` (FR-004, FR-005, SC-003).
- No delay-figure field, table, or computation exists anywhere in this feature's code (FR-007, SC-004).
