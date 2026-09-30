# Data Model: Queue Token Issuance Under Concurrent Requests

**No schema change.** There are no new tables, columns, indexes or Flyway migrations.

## Entities involved (existing)

### Session (`session`)
- Owns one token sequence. It is now also the **lock owner** for issuance: the issuing transaction takes a row lock on it (`FOR NO KEY UPDATE`) and holds it until commit (research.md Decision 1).
- No column changes. Session cancellation (whole or partial) does **not** write this row. It records itself in `session_cancellation`, so it does not wait on the issuance lock. The race where a session is cancelled at the same moment is unchanged and out of scope (plan.md Risks, analysis F1).

### Slot, token kind (`slot`, `token_number` not null, `start_time` null)
- Invariant, unchanged and enforced by `uq_slot_session_token`: at most one Slot per `(session_id, token_number)`.
- Invariant, **newly guaranteed** (FR-002, FR-008): for each session, the committed token numbers are exactly `1..N`, with no gaps.
- Invariant, **newly guaranteed** (FR-008, SC-007): every token Slot is referenced by exactly one Booking, created in the same transaction. There is no token without a booking.

### Booking (`booking`)
- Unchanged. Created in the same transaction as its token on all three paths.

### Booking attempt log (`booking_attempt_log`, from 060)
- Unchanged. On the patient queue path, the admitted row is still committed by the gate before the booking transaction starts (as in #20). Its flip to `SUCCESS` now commits with the booking.

## Token lifecycle

| Step | Before 067 | After 067 |
|---|---|---|
| Read next number | `max + 1`, unlocked, so racers collide | `max + 1` under the Session row lock, so no collisions |
| Insert token | own commit (queue paths) / in caller txn (walk-in) | always in the caller's booking/walk-in transaction |
| Booking fails after insert | token stays `BOOKED` with no booking (orphan) | token rolled back, number reused by next booking |
| Lost race | retry ×5, then 503 (queue) / whole registration fails (walk-in) | cannot happen; only a >5s lock wait → 503 `TOKEN_ISSUANCE_FAILED` |

## Existing data

Orphan tokens created before this change (`BOOKED` token slots with no booking) are **not** cleaned up by this feature. They would need a data fix, and whether any exist in a real database is unknown. See quickstart.md's check query. If some are found, cleaning them up is a separate, reviewed decision, not part of this change.
