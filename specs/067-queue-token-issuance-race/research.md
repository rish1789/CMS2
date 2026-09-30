# Research: Queue Token Issuance Under Concurrent Requests

All facts below were read from the code on `main` at `5391de4` (2026-09-30).

## Current state (verified)

| Path | Entry point | Transaction today | How the token is issued |
|---|---|---|---|
| Patient self-service queue booking | `PatientQueueBookingService.bookSlot` | None at the top. The 060 gate runs in its own transaction first. `findOrCreatePatient` is its own transaction. Token and booking are separate, independently committing steps. | `QueueSlotService.issueNextSlot` |
| Staff-assisted queue booking | `StaffQueueBookingService.bookSlot` | None at the top. Same separate steps. | `QueueSlotService.issueNextSlot` |
| Front-desk walk-in | `FrontDeskWalkInService.register` | One `@Transactional` for everything. | `issueNextWalkInSlot` (fixed-time) or `issueNextSlot` (queue) |

`QueueSlotService.issueWithRetry` loops up to `MAX_ATTEMPTS = 5` around `attemptIssueSlot`, which reads `max(token_number) + 1` and inserts. The unique index `uq_slot_session_token` refuses a duplicate number.

**Defect 1, lost races are not truly retried.** `attemptIssueSlot` is `@Transactional`, but it is called on `this`, so Spring's proxy is bypassed and the annotation does nothing.
- *Standalone*, with no outer transaction: each `save` is its own repository transaction, so a collision surfaces inside the loop and is retried. But the loop gives up after 5 losses. With 20 concurrent callers, one caller can lose 5 times, which produces `TokenIssuanceFailedException` → **503 `TOKEN_ISSUANCE_FAILED`**. This is what `QueueSlotIssuanceConcurrencyTest` shows intermittently (CI run 36675307866).
- *Inside the walk-in transaction*: `save` only queues the INSERT. The collision surfaces later, at `bookingRepository.saveAndFlush`, outside the loop, and PostgreSQL has already aborted the transaction. **The walk-in registration fails outright, with no retry.** (From reading the code and PB-003's register entry. Not yet reproduced, see T-plan.)

**Defect 2, orphan tokens.** On both queue booking paths the token commits before the booking. If the booking step then fails, the token stays with status `BOOKED` and no booking. Since 064 (V40) mints tokens as `BOOKED`, `QueuePositionService` counts such a token as a patient waiting ahead. The orphan inflates every later patient's position and permanently uses up the number.

## Decision 1 - Serialize issuance per session with a row lock on the Session

**Decision**: Before computing the next number, lock the Session's own row, then read `max + 1`, insert the token, and finish the booking or walk-in, all in the same transaction. The lock is released at commit. The next waiter then reads the committed maximum. The lock is `PESSIMISTIC_WRITE`, which PostgreSQL executes as `SELECT … FOR NO KEY UPDATE`, taken through a new `SessionRepository.findWithLockById`.

**Rationale**:
- Collisions stop happening, instead of being detected and retried, so no retry budget can run out (FR-001). Numbers come out in commit order with no gaps (FR-002).
- Only the one Session row is locked, so different sessions never wait on each other (FR-006).
- It is the same idiom this codebase already uses for patient accounts (060 Decision 1, 066). It needs no new table, sequence or migration (spec Assumption: no schema change).
- `FOR NO KEY UPDATE` does not conflict with the `FOR KEY SHARE` locks that the Slot and Booking inserts take on their foreign keys. The transaction therefore never blocks on its own lock.
- Principle IV ("close duplicate-creation races at the data layer") is met twice: by the row lock, and by the unchanged `uq_slot_session_token` safety net.

**Alternatives considered**:
- *Fix the self-invocation and keep optimistic retry, with more attempts.* Rejected. It still fails under a big enough burst, just more rarely. It also cannot work inside the walk-in transaction, because PostgreSQL aborts the whole transaction on the first collision. And it keeps two committing steps, so Defect 2 remains.
- *A per-session counter column or a database sequence.* Rejected (Principle II). It needs a migration and a new invariant to maintain, and a sequence leaves gaps on rollback, which violates FR-002.
- *Advisory locks* (as used for schedule overlap in 014). This would work, but a plain row lock on the natural owner, the Session, is simpler and self-documenting. The advisory-lock precedent exists because schedules had no single row to lock.

## Decision 2 - One transaction per queue booking (supersedes 022's "two separate atomic units")

**Decision**: `PatientQueueBookingService.bookSlot` and `StaffQueueBookingService.bookSlot` run patient resolution, token issuance and booking creation in **one** transaction, through a `TransactionTemplate`, the same approach #20 took for `PatientBookingService`. `QueueSlotService.issueNextSlot`/`issueNextWalkInSlot` become plain `@Transactional` (REQUIRED) public methods. They join the caller's transaction, or open their own when called standalone, which is the case in `QueueSlotIssuanceConcurrencyTest`. The retry loop and `MAX_ATTEMPTS` are removed, because they have nothing left to retry.

**Rationale**:
- A failed booking now rolls its token back, so no orphan remains and the next booking reuses the number (FR-008, SC-007, User Story 4).
- Walk-in registration is already a single transaction, so there it simply starts working (FR-003, FR-005).
- **Why this supersedes 022's research decision**: 022 accepted orphans as "operationally-harmless" because they were `OPEN` then, and because the one-transaction option "defeats `issueNextSlot`'s retry closure". Both premises are gone. Since 064, orphans are `BOOKED` and distort queue positions. With Decision 1, there is no retry closure left to defeat. The clarification of 2026-09-30 (spec, option A) makes the change explicit.

**Alternatives considered**: Keep the two steps and compensate by deleting the token when the booking fails. Rejected: a crash between the steps still orphans the token, and it adds a compensation path to maintain (Principle II).

## Decision 3 - The 060 gate stays outside the booking transaction on the patient queue path

**Decision**: On the patient queue path, `checkAndRecordAttempt` keeps running in its own transaction before the booking transaction, exactly as merged in #20. `recordSuccess` moves inside the booking transaction, so the success flag commits together with the booking.

**Rationale**: #20 established that the gate commits the admitted attempt row up front, so a later failure is already counted (060 FR-008) and no second connection is needed after the fact. Moving the gate inside would reintroduce the patient-row lock being held across the whole booking, and put the rejection recording back inside a doomed transaction. Neither is needed here.

## Decision 4 - Lock order and connection use

**Decision**: The fixed lock order inside a queue booking transaction is **patient-account row first** (taken by 066's `findOrCreatePatient`, patient path only), **then the Session row**. The walk-in and staff paths take only the Session lock. While a transaction holds the Session lock, it must not open a second database connection: no `REQUIRES_NEW` inside it.

**Rationale**: Every path acquires locks in the same order, so two bookings cannot deadlock by taking them in opposite orders. The no-second-connection rule is the lesson from #20's first attempt: a burst larger than the pool (10) stalls every request when a lock holder waits for a second connection. The concurrency tests use 20 callers, more than the pool, so they would catch a regression.

## Decision 5 - Bounded wait (FR-007)

**Decision**: The issuance transaction runs `SET LOCAL lock_timeout = '5s'` before taking the Session lock. A lock-wait timeout (`PessimisticLockingFailureException`/`CannotAcquireLockException`) is translated to the existing `TokenIssuanceFailedException`. That exception already maps to **503 `TOKEN_ISSUANCE_FAILED`**, documented as "retry later" in `BookingExceptionHandler`. No new error code or message is introduced (FR-004).

**Rationale**: A lock holder only holds the lock for one booking's inserts, which takes milliseconds. Five seconds therefore means something is badly wrong, not ordinary busyness, so SC-001's 20-request bursts will never reach it. PostgreSQL's `FOR UPDATE` syntax has no wait-with-timeout clause, so `lock_timeout` is the dependable mechanism. `SET LOCAL` confines it to the one transaction. *Unverified*: whether Hibernate's `jakarta.persistence.lock.timeout` hint would do the same on this Hibernate version. The lock-timeout test (Decision 6, item 5) proves whichever mechanism is used, so the choice is checked by a test rather than assumed.

**Alternatives considered**: No bound at all (FR-007 violated: a request could hang until the 30s connection-pool timeout); `NOWAIT` (rejected: it refuses on *any* contention, which is exactly the failure being fixed).

## Decision 6 - Test strategy (test-first)

Red first, against current `main`:
1. `QueueSlotIssuanceConcurrencyTest` made **deterministic**: 20 concurrent standalone issuances, repeated 10 times inside the test. It must pass all 10 (SC-001/SC-002). Today it fails intermittently.
2. New: concurrent **walk-in registrations** for one session, with a queue session and a fixed-time session's walk-in line (User Story 2, SC-003). Expected red today, because the first collision aborts the registration. This also verifies the not-yet-reproduced walk-in claim.
3. New: concurrent **patient and staff queue bookings** mixed on one session (User Story 1, scenario 2).
4. New: a booking that **fails after issuance** leaves no token, and the next booking reuses the number (User Story 4, SC-007). Forced by an appointment type that fails the booking step.
5. New: **lock-timeout** path. Hold the Session lock in one transaction and verify a concurrent request is refused with 503 `TOKEN_ISSUANCE_FAILED` after the bound, leaving nothing behind (FR-007).
6. Existing refusal tests (`QueueSlotIssuanceRejectionTest`, 062 gating, 060 limits, duplicate walk-in) must stay green unchanged (FR-004, SC-005).

Unit tests that stub `QueueSlotService` (`FrontDeskWalkInServiceTest`, `QueueSlotServiceWalkInTest`) are updated only where the retry loop is removed.
