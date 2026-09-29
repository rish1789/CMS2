# Research: Patient-Linking Same-Account Race (066)

All findings were traced in code at `main` `715738b`.

## R1: Why the losing request fails today

**Finding**:
- `PatientLinkingService.findOrCreatePatient` inserts the new Patient record with `saveAndFlush`.
- On `uq_patient_clinic_account` it catches the `DataAccessException` and re-reads the winner's row inside the same transaction.
- PostgreSQL marks a transaction as aborted after any failed statement, so the re-read fails with `current transaction is aborted, commands ignored until end of transaction block`. That is the exact error in `PatientLinkingSameAccountRaceTest`.
- Independently, Spring marks the transaction rollback-only when the repository call throws. So even a successful re-read would end in `UnexpectedRollbackException` at commit.

**Consequence**: No approach that lets the losing INSERT fail inside the caller's transaction and then "recovers" can work. The loser must never issue the conflicting INSERT, or it must issue it somewhere a failure is harmless.

## R2: When the race is actually reachable

| Caller | Transaction shape | Same-account serialization today |
|---|---|---|
| `PatientBookingService.bookSlot` (fixed-time) | One `@Transactional` spanning the protection gate, linking and the booking insert | Serialized **only while the booking limit is enabled** (the default). `BookingProtectionService.checkBookingLimit` takes `patientAccountRepository.findWithLockById` (PESSIMISTIC_WRITE) and holds it until commit (060 research Decision 1). With the booking limit disabled, there is no lock and the race is reachable. |
| `PatientQueueBookingService.bookSlot` (queue) | Not `@Transactional` (022 design). `checkAndRecordAttempt`, `findOrCreatePatient` and the booking save each run in their own transaction. | The gate's lock is released when `checkAndRecordAttempt` commits, so the race is **always reachable**. |
| Direct callers (the service contract, and `PatientLinkingSameAccountRaceTest`) | Each call is its own transaction | None |

**Consequence**:
- The fix belongs in `findOrCreatePatient` itself (the 009 contract). It must not depend on a caller's settings.
- On the queue path, the Patient record already commits separately from the booking, as existing behaviour. The spec's FR-003 was corrected to say this; it is not changed here.

## R3: Chosen design: serialize same-account linking on the account row

**Decision**:
- `findOrCreatePatient` loads the Patient Account with the existing `PatientAccountRepository.findWithLockById` (PESSIMISTIC_WRITE, `SELECT … FOR UPDATE`) instead of `findById`.
- The lock is held until the surrounding transaction ends:
  - **Fixed-time path:** that is the booking's commit or rollback.
  - **Queue path and direct calls:** that is the linking call's own commit.
- **A second concurrent caller for the same account blocks** on the lock until the first ends. Under PostgreSQL's default READ COMMITTED, its next statement (the FR-002 existing-link query) then sees the first caller's committed row and reuses it. No conflicting INSERT is ever issued, so no transaction is poisoned. This covers FR-001 and SC-001/SC-002.
- **If the first caller rolls back**, its uncommitted Patient row disappears. The second caller finds no link and creates the record itself (FR-004).
- **The lock serializes the whole method**, including the FR-003 phone-match path, so two same-account calls can never both try to link the same walk-in record.
- **`uq_patient_clinic_account` stays unchanged** as the data-layer backstop (FR-002, Constitution IV). A violation can now only come from a writer that bypasses this method. Such a violation must fail loudly (FR-006), so the broken catch-and-re-read block is removed rather than kept as dead or misleading code.

**Rationale**:
- It reuses a lock method and pattern that already exist for exactly this purpose (060), so it adds no new repository method, native SQL or configuration (Principle II).
- The lock lives in the caller's transaction, so the fixed-time booking stays all-or-nothing (FR-003). This is the property the separate-transaction alternative breaks.
- It is correct whatever the caller's transaction shape or booking-limit setting is.

**Deadlock and contention review**:
- **Fixed-time path, booking limit enabled:** the gate already holds this exact lock in the same transaction, so taking it again is a no-op. There is no new contention.
- **Fixed-time path, booking limit disabled:** before linking, a transaction has written only its own `booking_attempt_log` row. After linking, the winner locks its slot and booking rows, which the waiting loser has only read, never locked. The loser holds nothing the winner needs, so there is no lock cycle.
- **Other lockers of `patient_account`:** the only other `findWithLockById` caller is the 060 gate, which takes the same lock first. No code path locks a `patient` row and then its account, so there is no inverted lock order.
- **Cost:** concurrent bookings by *different* accounts lock different rows and don't contend. Only same-account concurrency waits, which is the intended serialization, and the wait is bounded by one booking transaction.

**Alternatives considered**:

| Alternative | Rejected because |
|---|---|
| `INSERT … ON CONFLICT (clinic_id, patient_account_id) WHERE patient_account_id IS NOT NULL DO NOTHING` via a native `@Modifying` query, then re-read | Correct and atomic, but it would be the first native SQL in the codebase and needs a new repository method. It duplicates the partial-index predicate, which drifts if V5's index ever changes. The lock achieves the same result with an existing method (Principle II). This was the chat-time recommendation before R2's lock finding. |
| Create the record in a separate `REQUIRES_NEW` transaction, then re-read | Breaks FR-003: a Patient record would persist when the fixed-time booking later fails. It also holds two connections at once. |
| Savepoint (`Propagation.NESTED`) around the INSERT | `JpaTransactionManager` does not allow nested transactions by default. Enabling it is a global transaction-manager change, far outside this fix's scope. |
| Retry the whole booking on conflict | Needs a new retry layer across both booking services, and re-runs the protection gate (double-counting attempts). Much more complex for the same result. |
| Rely on the 060 booking-limit lock | Only covers the fixed-time path, and only while the booking limit is enabled (R2). |

## R4: Test strategy (Constitution I: test-first)

- **Red today:** `PatientLinkingSameAccountRaceTest` (FR-001, SC-001/002/005). It stays unchanged.
- **New, winner rollback (FR-004):** transaction A calls `findOrCreatePatient` and holds its transaction open on a latch. Call B for the same account and clinic starts and blocks. A then rolls back, and B must succeed with exactly one row. Expected to fail today: B's INSERT waits on A's uncommitted row, and once A rolls back B proceeds. Whether B then fails depends on timing, so this test is confirmed red or green only when written (see tasks).
- **New, atomicity guard (FR-003, SC-003):** a fixed-time booking whose booking INSERT fails after linking must leave zero Patient records. This protects against a future REQUIRES_NEW-style regression. It should pass both before and after the change, as a characterization test.
- **New, end-to-end queue path (FR-007):** two concurrent queue bookings by one account at a new clinic must both succeed and share one Patient record. Expected red today, because the queue path has no lock. To be confirmed when written.
- **New, end-to-end fixed-time path with booking limit disabled (FR-007):** two concurrent slot bookings for different slots must both succeed with one Patient record. Expected red today; to be confirmed when written.
- **Unchanged regression suite (SC-004):** the existing 009 tests for existing-link reuse, phone match, linked-record protection and walk-in uniqueness, plus the 021/022/060 booking tests.
