# Research: Queue/Token Session Slot-on-Booking Generation

## Decision: A partial unique index `(session_id, token_number) WHERE token_number IS NOT NULL`, plus a non-transactional-outer / fresh-transaction-per-attempt retry loop

**Rationale**: The database constraint is the actual, load-bearing guarantee (Constitution IV) — it makes two Slots with the same `(session, tokenNumber)` pair impossible to ever both commit, full stop. On top of that, `issueNextSlot` reuses 011's own already-proven pattern exactly: a plain (non-`@Transactional`) outer method retries a bounded number of times, each retry calling a fresh `@Transactional` inner method that starts (and, on success, commits) its own independent transaction. A constraint violation on one attempt only aborts *that* attempt's own transaction — the next retry starts clean, re-reads the (now-updated) max token number, and tries again. This is deliberately more proactive than 011's own "log and move on" recovery, because here the two racing callers are not in a "whoever committed first wins, the rest can be skipped" relationship (011's schedule generation) — every caller genuinely needs *a* new token, so every one of them must eventually succeed.

**Alternatives considered**: SAVEPOINT-based nested-transaction retry within one outer transaction — rejected, same reasoning as 011's research.md: no precedent in this codebase, more complexity than the already-proven outer-retry/fresh-transaction pattern for no additional correctness benefit. A `SELECT ... FOR UPDATE` row lock on the Session (or a dedicated per-Session counter row) instead of optimistic retry — rejected as unnecessary additional locking machinery (Principle II) for a bounded, low-conflict-probability retry loop that already correctly resolves the race.

## Decision: `Slot` gains a nullable `tokenNumber`; `startTime`/`endTime` become nullable

**Rationale**: A Queue-mode Slot and a Fixed-Time Slot are genuinely two different shapes of the same underlying concept ("one bookable unit within a Session") — extending the existing entity in place (rather than a parallel `QueueSlot` entity) means every future feature that needs to treat "a Slot" uniformly (023's delay tracking explicitly excludes queue-mode, 024's queue-position tracking, a future booking's "attach to this Slot" logic) can keep doing so without a type-branching join. This mirrors this backlog's own precedent for exactly this situation (006/008 extending `DoctorProfile` in place for a new, later-arriving concern).

**Alternatives considered**: A separate `QueueSlot` entity with its own table — rejected; it would immediately force every future cross-mode Slot query (e.g. "list all of today's bookable units for this doctor, any mode") into a union across two tables for a distinction (`isBuffer`/`startTime` vs `tokenNumber`) that's naturally expressed as a few nullable columns on one entity.

## Decision: `QueueSlotService.issueNextSlot(UUID sessionId)` — no HTTP endpoint

**Rationale**: Identical reasoning to 009's and 011's precedent — 018 (the only real eventual caller) doesn't exist yet, and guessing at a booking-shaped endpoint now would be exactly the kind of speculative surface this backlog has consistently avoided building ahead of the feature that actually defines what a booking submission looks like.

**Alternatives considered**: A speculative internal `/api/v1/sessions/{sessionId}/slots` endpoint — rejected; nothing calls it yet.

## Decision: `findMaxTokenNumberBySession_Id` returns the current max (or empty/null), not a running counter column

**Rationale**: A separate "next token counter" column/row (on `Session`, or a dedicated counter table) would need its own concurrency handling identical in shape to what the `Slot` table's own constraint already provides "for free" once combined with the retry loop — reading `MAX(tokenNumber)` directly from the `Slot` rows that already exist is simpler and has one less piece of mutable state to keep consistent with reality.

**Alternatives considered**: A `nextToken` counter field on `Session`, incremented atomically per issuance — rejected as an extra piece of state duplicating what's already derivable from the `Slot` table itself (Principle II).
