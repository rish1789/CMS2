# Quickstart: Queue/Token Session Slot-on-Booking Generation

See [data-model.md](./data-model.md) and [contracts/queue-slot-issuance.md](./contracts/queue-slot-issuance.md). No HTTP endpoint — exercised via direct `QueueSlotService` calls.

## Scenario 1 — Sequential issuance

1. Generate a Queue/Token Session (011/013's Schedule → Session path; zero Slots).
2. Call `issueNextSlot(sessionId)` five times in a row. **Expect**: token numbers 1, 2, 3, 4, 5, each call returning a new `Slot`.

## Scenario 2 — Never-reused tokens survive "cancellation"

1. From Scenario 1, treat the Slot with token 3 as if cancelled (no actual cancellation mechanism exists yet — just note its id).
2. Call `issueNextSlot(sessionId)` again. **Expect**: token 6 — never token 3 again.

## Scenario 3 — Concurrent issuance

1. From a fresh Session, issue 20 concurrent calls to `issueNextSlot(sessionId)`.
2. Inspect all resulting Slots' token numbers. **Expect**: exactly the set `{1..20}`, no duplicates, no gaps.

## Scenario 4 — Rejections

1. Call `issueNextSlot` against a Fixed-Time Session's id. **Expect**: throws `NotAQueueSessionException`.
2. Call `issueNextSlot` against an unknown session id. **Expect**: throws `SessionNotFoundException`.
