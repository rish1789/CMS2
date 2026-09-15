# Data Model: Queue/Token Session Slot-on-Booking Generation

## `Slot` (extends 012's existing entity)

| Field | Type | Change |
|---|---|---|
| `startTime` | `LocalTime`, now **nullable** | `null` for a Queue-mode Slot |
| `endTime` | `LocalTime`, now **nullable** | `null` for a Queue-mode Slot |
| `tokenNumber` | `Integer`, **new**, nullable | Set only by Queue-mode issuance; `null` for a Fixed-Time Slot |

New constructor: `Slot(Session session, int tokenNumber)` — `startTime`/`endTime`/`isBuffer` default to `null`/`null`/`false`, `status = OPEN`. The existing Fixed-Time constructor (`Slot(Session, LocalTime, LocalTime, boolean)`) is unchanged and leaves `tokenNumber` `null`.

**DB constraint**: partial unique index `(session_id, token_number) WHERE token_number IS NOT NULL` — the data-layer guarantee behind FR-003/SC-002 (research.md).

## `SlotRepository` (extended)

`findMaxTokenNumberBySession_Id(UUID sessionId) -> Optional<Integer>` — the current highest token number issued for a Session (empty if none).

## Service flow

### `QueueSlotService.issueNextSlot(UUID sessionId) -> Slot` (not itself `@Transactional`)

```text
for attempt in 1..MAX_ATTEMPTS:
    try:
        return attemptIssueSlot(sessionId)   // @Transactional, own fresh transaction per call
    catch DataIntegrityViolationException:
        continue   // lost the race; retry with a freshly-read max on the next attempt
throw TokenIssuanceFailedException(sessionId)   // exhausted retries - only reachable under pathological contention
```

### `QueueSlotService.attemptIssueSlot(UUID sessionId) -> Slot` (`@Transactional`, package-private)

1. Load `Session` by id, or `SessionNotFoundException` (FR-005).
2. If `session.getMode() != ScheduleMode.QUEUE`, throw `NotAQueueSessionException` (FR-004) — checked before any read/write of `Slot` data.
3. `next = slotRepository.findMaxTokenNumberBySession_Id(sessionId).orElse(0) + 1`.
4. Save and return `new Slot(session, next)`. The unique index throws here if a concurrent attempt already claimed `next` — propagates to the outer retry loop (research.md).

## Request/Response contract

See `contracts/queue-slot-issuance.md` — no HTTP endpoint (spec Assumptions); the contract is `QueueSlotService`'s Java interface shape.
