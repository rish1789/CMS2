# Tasks: Queue Token Issuance Under Concurrent Requests

**Input**: Design documents from `specs/067-queue-token-issuance-race/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/token-issuance.md, quickstart.md

**Tests**: Required. Constitution Principle I (test-first, non-negotiable) and research.md Decision 6.

**Organization note (deliberate deviation from the template)**: One change, the Session row lock in `QueueSlotService`, fixes User Stories 1 to 4 at once. If each story's tests lived in its own phase, the later stories' tests would already pass by the time they were written, so the test-first rule could not be honoured. **All new and tightened tests are therefore written and run red in Phase 2**, against unchanged code. The story phases hold the implementation and each story's green verification.

**Environment**: Integration tests need Docker Desktop running. On this Windows machine, run Gradle from `backend/` with `${env:api.version} = "1.44"` set in the same PowerShell session (quickstart.md).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story the task belongs to (US1–US4)

---

## Phase 1: Setup

**Purpose**: Confirm the starting point on the feature branch.

- [X] T001 Confirm branch `claude/067-queue-token-issuance-race` is based on `origin/main` at or after `5391de4`, and that `backend/src/test/resources/application.properties` from PR #22 is either merged into `main` or absent. If #22 is not yet merged, note it in this file: the scheduled sweeps may still fire during this feature's tests, but they do not touch token slots in `QUEUE` sessions.
  - *Result 2026-09-30*: the branch is based on `5391de4`. #22 is **open, not merged**, so there is no test `application.properties` on this branch. The sweeps leave queue tokens and untimed walk-ins alone (`NoShowDetectionTest.aBookedQueueSlotIsUntouchedRegardlessOfElapsedTime`, `UntimedSlotSweepsTest`), and all 067 fixtures are tomorrow-dated.
- [X] T002 Run the existing issuance tests on unchanged code and record the results in this file under Phase 2's red-run notes: `.\gradlew.bat test --tests "*QueueSlotIssuance*" --tests "*FrontDeskWalkIn*" --tests "*QueueBooking*"` from `backend/`.
  - *Result 2026-09-30* (after the known OneDrive `build/` AccessDenied error was cleared by deleting `backend/build`): **47 tests, 2 failed**, both with PB-003's `TokenIssuanceFailedException … after repeated attempts`. The failures were `QueueSlotIssuanceConcurrencyTest.twentyConcurrentCallsEachReceiveADistinctSequentialToken` and **`QueueBookingConcurrencyTest.concurrentQueueBookingsAgainstOneSessionEachGetADistinctNeverReusedToken`**, an existing test not named in the plan. It must also be green after US1 (added to T017).

---

## Phase 2: Foundational - failing tests first (blocks all implementation)

**Purpose**: Write every new or tightened test, then run each one **red** on unchanged code (quickstart.md §1).

**⚠️ CRITICAL**: No implementation task in Phases 3 to 6 may start until T011 is complete and each test's red result is recorded.

Fixture rule for all tests below: date every Session **tomorrow** (`LocalDate.now().plusDays(1)`), never today. A today-dated 09:00–13:00 session is "not accepting" after 13:00, which made earlier fixtures depend on the time of day (HANDOFF Part 14).

- [X] T003 Add a `saveQueueSessionOn(Clinic, DoctorProfile, LocalDate)` helper, plus a fixed-time equivalent if it doesn't exist, to `backend/src/test/java/com/cms/booking/integration/AbstractQueueBookingIntegrationTest.java`, generating the session for the given date via `sessionGenerationService.generate(date)` and returning that date's session. Mirror the existing `saveQueueSession`, which is left unchanged for current callers.
- [X] T004 [P] Tighten `backend/src/test/java/com/cms/scheduling/integration/QueueSlotIssuanceConcurrencyTest.java`: 20 concurrent `issueNextSlot` calls on 10 threads, **repeated 10 times** (a fresh tomorrow-dated session each repeat). Assert every call succeeds and each session's tokens are exactly 1..20, sorted with no duplicates (US1, SC-001, SC-002, SC-006).
- [X] T005 [P] Create `backend/src/test/java/com/cms/booking/integration/QueueTokenConcurrentBookingTest.java` extending `AbstractQueueBookingIntegrationTest`. Use 20 concurrent bookings on one tomorrow-dated queue session: 10 through `PatientQueueBookingService.bookSlot` (distinct `PatientAccount`s, so the 060 per-patient rate limit and booking limit are not hit) and 10 through `StaffQueueBookingService.bookSlot`. Assert all 20 succeed, each booking has a distinct token, and the tokens are exactly 1..20 (US1 scenario 2).
- [X] T006 [P] Create `backend/src/test/java/com/cms/booking/integration/WalkInConcurrentRegistrationTest.java`. Model it on the existing `FrontDeskWalkInRegistrationTest`, reusing its base class `AbstractDeVerificationCascadeIntegrationTest` and its request helper shape. Case A: 10 concurrent walk-in registrations for a tomorrow-dated **queue** session, all succeed, tokens exactly 1..10. Case B: the same for a tomorrow-dated **fixed-time** session's walk-in line, all succeed, walk-in positions exactly 1..10. Use distinct patient names/phones, so the duplicate-walk-in rule doesn't apply (US2, SC-003).
- [X] T007 [P] Add to `WalkInConcurrentRegistrationTest.java`: 5 valid registrations plus 1 that violates the duplicate-walk-in rule, submitted concurrently. The invalid one is refused with its existing error code, the 5 valid ones succeed with tokens 1..5, and no extra token exists (US2 scenario 2, FR-005).
- [X] T008 [P] Create `backend/src/test/java/com/cms/booking/integration/QueueBookingFailureLeavesNoTokenTest.java`. Force one booking INSERT to fail, once on the patient path and once on the staff path. *(Implemented with a test-only Postgres `BEFORE INSERT` trigger on `booking` that rejects one patient's row, instead of the planned `@SpyBean BookingRepository`. Spying on a Spring Data proxy is fragile, and the trigger fails in the real database like a genuine failure. The trigger is dropped after each test.)* Assert: the booking attempt fails; **no token Slot without a Booking** exists for the session; the next successful booking receives the number the failed one would have had; and `QueuePositionService` for a later booking counts only real waiting bookings (US4, FR-008, SC-007).
- [X] T009 [P] Create `backend/src/test/java/com/cms/booking/integration/TokenIssuanceLockTimeoutTest.java` *(in `booking/integration`, not `scheduling/integration` as planned: it needs MockMvc and the patient-account fixtures of `AbstractQueueBookingIntegrationTest`)*. In one thread, open a transaction (`TransactionTemplate`) that takes the Session row lock with `SELECT … FOR NO KEY UPDATE` via native query, and hold it for 8 s. In another, call `issueNextSlot` for the same session. Assert it throws `TokenIssuanceFailedException` in 5–7 s and that no token was created. Also assert, via MockMvc on the patient queue-booking endpoint, that the HTTP response is **503 `TOKEN_ISSUANCE_FAILED`** (FR-007, contracts/token-issuance.md). **Add a second case (FR-006, analysis C1):** while the lock on session A is held, an `issueNextSlot` call for a *different* session B completes in under 1 s and receives token 1.
- [X] T010 [P] Create `backend/src/test/java/com/cms/booking/integration/QueueIssuanceRefusalUnderConcurrencyTest.java`: concurrent valid token requests for an accepting tomorrow-dated queue session, mixed with requests for a *cancelled* session and for a *fixed-time* session through the queue path. Assert the invalid ones get their existing refusals (`SESSION_NOT_ACCEPTING_BOOKINGS` and `NOT_A_QUEUE_SESSION`, the codes in `BookingExceptionHandler` today) and the valid ones all succeed with tokens 1..N (US3, FR-004).
- [X] T011 **Red run**: run T004–T010 on unchanged code (quickstart.md §1) and record each result here with the failure message. Expected: T004 fails in at least one repeat; T005 is intermittent or fails; T006 case A and/or B fails; T008 leaves an orphan token; T009 is not refused within the bound. T007 and T010 **may pass today**; that is acceptable, since they guard FR-004/FR-005 against regression. Record which. The FR-006 case in T009 (a different session is not blocked) **may pass today**, which is acceptable; it guards against the new lock regressing it. If any of T004, T005, T006, T008 or the FR-007 case of T009 passes on unchanged code, stop and revisit research.md before implementing.
  **Also record an SC-004 baseline (analysis C2):** with a temporary timing harness that is **not committed**, time 50 sequential, uncontended patient queue bookings, each on its own tomorrow-dated session, on unchanged code. Record the median and 95th-percentile per booking here, with the machine (this Windows dev box) noted.

**Red-run results (T011, 2026-09-30, unchanged code, local Docker)**: 13 tests, 10 failed. Every result matches the prediction.
- T004 `QueueSlotIssuanceConcurrencyTest`: FAIL, `TokenIssuanceFailedException … after repeated attempts`.
- Existing `QueueBookingConcurrencyTest`: FAIL, same PB-003 error.
- T005 `QueueTokenConcurrentBookingTest`: FAIL, same PB-003 error.
- T006 `WalkInConcurrentRegistrationTest`, queue and fixed-time: FAIL, **unhandled `DataIntegrityViolationException` on `uq_slot_session_token`**, so the request fails with a server error. This **verifies** the spec's walk-in claim, and shows it is worse than stated: an unhandled error, not a clean refusal.
- T007, duplicate walk-in in a burst: FAIL, same collision. Allowed either way.
- T008, patient and staff: FAIL, `expected: 2 but was: 3`. The orphan token burned number 2.
- T009, bound (service and HTTP): FAIL, never refused (HTTP 201).
- T009, FR-006 case: PASS (allowed; a guard against regression).
- T010: PASS (allowed; a guard against regression).
- **SC-004 baseline** (temporary harness `ZzTimingHarness067Test`, not committed): 50 uncontended patient queue bookings, **median 117.1 ms, p95 142.5 ms** per booking. Pass threshold after the fix: median ≤ 140.5 ms (+20%, which is larger than +10 ms).

**Checkpoint**: The red results are recorded, and implementation may begin.

---

## Phase 3: User Story 1 - Simultaneous queue bookings all get a token (Priority: P1) 🎯 MVP

**Goal**: Concurrent token requests for one session all succeed with tokens 1..N, standalone and through both queue booking paths, with a bounded wait (FR-001, FR-002, FR-006, FR-007).

**Independent Test**: T004, T005 and T009 pass.

- [X] T012 [US1] Add `@Lock(LockModeType.PESSIMISTIC_WRITE) Optional<Session> findWithLockById(UUID id)` to `backend/src/main/java/com/cms/scheduling/repository/SessionRepository.java`, mirroring `PatientAccountRepository.findWithLockById`.
- [X] T013 [US1] Rewrite issuance in `backend/src/main/java/com/cms/scheduling/service/QueueSlotService.java`:
  - make `issueNextSlot`/`issueNextWalkInSlot` public `@Transactional` (REQUIRED);
  - in each, first execute `SET LOCAL lock_timeout = '5s'` through an injected `EntityManager` native query;
  - then `sessionRepository.findWithLockById(sessionId)`, with the existing not-found and mode checks unchanged;
  - then read `findMaxTokenNumberBySession_Id + 1`, build the Slot as today (status `BOOKED`), and `save`;
  - translate a lock-wait failure (`PessimisticLockingFailureException`, which includes `CannotAcquireLockException`, and `jakarta.persistence.LockTimeoutException`) into `TokenIssuanceFailedException`;
  - **delete** `issueWithRetry`, `attemptIssueSlot` and `MAX_ATTEMPTS`;
  - update the class Javadoc to point at 067 research Decisions 1, 4 and 5.
- [X] T014 [US1] Make `PatientQueueBookingService.bookSlot` in `backend/src/main/java/com/cms/booking/service/PatientQueueBookingService.java` run `doBookSlot` (patient linking, issuance, booking save) **and** `bookingProtectionService.recordSuccess` inside one `TransactionTemplate.execute`. Build it from an injected `PlatformTransactionManager`, as `PatientBookingService` does since #20. Keep the 062 clinic check and the 060 gate (`checkAndRecordAttempt` with its `recordAfterRollback` catch) **before and outside** that transaction (research Decision 3). Remove the obsolete "Convergence fix … non-transactional" comments that describe the old two-step flow, and point to 067 research Decision 2.
- [X] T015 [US1] Make `StaffQueueBookingService.bookSlot` in `backend/src/main/java/com/cms/booking/service/StaffQueueBookingService.java` run patient resolution, issuance and booking save inside one `TransactionTemplate.execute` (injected `PlatformTransactionManager`). Update the class Javadoc that currently says the method is "deliberately NOT @Transactional", with a pointer to 067 research Decision 2.
- [X] T016 [US1] Update every unit test that constructs these services or stubs the removed retry, to match the new constructors and behaviour: pass `mock(PlatformTransactionManager.class)` and a mocked `EntityManager` where needed. At minimum: `backend/src/test/java/com/cms/scheduling/unit/QueueSlotServiceWalkInTest.java`, `backend/src/test/java/com/cms/booking/unit/FrontDeskWalkInServiceTest.java`, `backend/src/test/java/com/cms/booking/unit/BookingPathsAvailabilityTest.java`, `backend/src/test/java/com/cms/booking/unit/RejectedClinicBookingRefusalTest.java`. Find the rest with `grep -rn "new PatientQueueBookingService(\|new StaffQueueBookingService(\|new QueueSlotService(" backend/src/test`. Do not weaken any assertion.
- [X] T017 [US1] Run T004, T005, T009, the existing `QueueBookingConcurrencyTest` (red at T002) and the unit tests from T016: all green (`spotlessApply` first).

*T017 result 2026-09-30*: **43 tests, 0 failed**. This covers T004, T005, T009 (all three cases), the existing `QueueBookingConcurrencyTest`, and the unit tests updated in T016 (`QueueSlotServiceWalkInTest`, `BookingPathsAvailabilityTest`, `RejectedClinicBookingRefusalTest`, `FrontDeskWalkInServiceTest`). `TokenIssuanceFailedException`'s message was reworded from "after repeated attempts" to "in time - please try again", since there is no retry any more. Code and status are unchanged, and no test or frontend code depends on the text (the frontend keys on the error code).

**Checkpoint**: US1 is complete. Every concurrent queue booking gets a token, and waits are bounded.

---

## Phase 4: User Story 2 - Simultaneous walk-in registrations never refused because of each other (Priority: P1)

**Goal**: Concurrent walk-in registrations all succeed, on queue sessions and on fixed-time walk-in lines (FR-003, FR-005).

**Independent Test**: T006 and T007 pass.

- [X] T018 [US2] Confirm `FrontDeskWalkInService.register` in `backend/src/main/java/com/cms/booking/service/FrontDeskWalkInService.java` needs no logic change: it is already one `@Transactional`, and `issueNext*Slot` now joins it and takes the Session lock. Confirm it contains no `REQUIRES_NEW` call and opens no second connection while the lock is held (research Decision 4). Fix only if found, and record the finding here.
- [X] T019 [US2] Run T006 and T007: green.

*T018 result*: no change needed. `register` is one `@Transactional`; `inboxItemService.createWalkInItem` joins it (`REQUIRED`), and there is no `REQUIRES_NEW` in `FrontDeskWalkInService`, `InboxItemService` or `QueuePositionService`.
*T019-T021 result 2026-09-30*: one run, **24 tests, 0 failed** across all 8 classes: `WalkInConcurrentRegistrationTest` (3), `QueueIssuanceRefusalUnderConcurrencyTest` (1), `QueueSlotIssuanceRejectionTest` (2), `RejectedClinicBookingRefusalTest` (unit 4 + integration 6), `BookingLimitConcurrencyTest` (1), `BookingRateLimitConcurrencyTest` (3), `FrontDeskWalkInRegistrationTest` (2), `QueueBookingFailureLeavesNoTokenTest` (2). The Gradle build then failed in `jacocoTestReport` with the known OneDrive "not a regular file" snapshot error, after all tests had passed. That is a local build-folder issue, not a test result.

**Checkpoint**: US1 and US2 are both complete.

---

## Phase 5: User Story 3 - Refusals only for real reasons (Priority: P2)

**Goal**: Every existing refusal keeps its code and message, alone or under concurrency (FR-004, SC-005).

**Independent Test**: T010 plus the existing refusal suites pass unchanged.

- [X] T020 [US3] Run T010 and the existing refusal tests unchanged: `QueueSlotIssuanceRejectionTest`, `RejectedClinicBookingRefusalTest` (unit and integration), `BookingLimitConcurrencyTest`, `BookingRateLimitConcurrencyTest`, and the duplicate-walk-in cases in `FrontDeskWalkInRegistrationTest`. All green. If any fails, fix the implementation, not the test.

**Checkpoint**: No refusal behaviour has changed.

---

## Phase 6: User Story 4 - A failed queue booking leaves no token behind (Priority: P2)

**Goal**: Queue bookings are all-or-nothing on both paths (FR-008, SC-007).

**Independent Test**: T008 passes.

- [X] T021 [US4] Run T008: green on both the patient and staff paths. If the patient path fails, check that `recordSuccess` runs inside the booking transaction and the gate outside it (T014).

**Checkpoint**: All four stories are complete.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T022 [P] Add a dated pointer at the top of the "Slot-issuance and Booking-creation are two separate atomic units" decision in `specs/022-queue-token-booking/research.md`: "Superseded 2026-09-30 by 067 research.md Decision 2 (064 made orphan tokens `BOOKED`; 067's Session lock removed the retry closure)." Do not delete the original text.
- [X] T023 [P] Update the `TOKEN_ISSUANCE_FAILED` row in `specs/022-queue-token-booking/contracts/queue-booking.md` to the new meaning in `specs/067-queue-token-issuance-race/contracts/token-issuance.md`.
- [X] T024 [P] Correct `TOKEN_ISSUANCE_FAILED` from **409** to **503** in `specs/063-front-desk-walk-in/contracts/front-desk-walk-in.md`, noting it is a documentation fix matching existing code (`BookingExceptionHandler`).
- [X] T025 [P] Mark PB-003 resolved by 067 in `docs/product-audit/07-BUG-AND-DEFECT-REGISTER.md`, and add the orphan-token defect as found and fixed by 067, linking to this spec.
- [X] T026 Repeat the T011 timing harness on the fixed code, on the same machine. **SC-004 passes if the median per booking is no more than 20% or 10 ms slower than the baseline, whichever is larger.** Record both numbers here, then delete the harness. Then run `.\gradlew.bat spotlessCheck`, then the affected packages (quickstart.md §2: `com.cms.scheduling.*`, `com.cms.booking.*`, `com.cms.protection.*`, `com.cms.patient.*`), then the **full** backend suite. Record exact pass/fail/skip counts here. The expected total is the previous total plus the new tests.
  - *SC-004 result 2026-09-30* (same machine, temporary harness since deleted): **median 121.7 ms against a 117.1 ms baseline (+4.6 ms, +4%) → PASS** (threshold 140.5 ms). p95 went from 142.5 to 161.6 ms (+13%). SC-004 is judged on the median, but the p95 rise is recorded here. It is one run each and may be partly noise; not repeated. `spotlessCheck` passes.
  - *Full-suite result 2026-09-30* (local, Docker, 34 min): **1,067 tests, 1 failed, 0 skipped**. That is `main`'s 1,057 plus 067's 10 new tests. The failure was `FrontDeskWalkInRegistrationTest.fixedTimeWalkInsJoinTheWalkInLineWithoutTouchingTimedSlots`: a booked 09:00 slot (today) was `NO_SHOW` where the test expected `BOOKED`. **Verified cause**: the per-minute no-show sweep firing mid-test, which is the bug PR #22 fixes (not merged on this branch's base). With #22's two files temporarily applied, and removed afterwards (tree verified clean), the test **passes** on the 067 code at the same time of day. The failure is unrelated to 067.
- [X] T027 Commit on `claude/067-queue-token-issuance-race`, push, and open a PR. The PR description covers what changed, what could go wrong (the plan's Risks section), checks passed or failed with exact counts, and how to undo it (revert the PR; no schema change). **Do not merge.** Report CI results with exact counts from the log.
  - *Result*: commits `ddb123f` (spec) and `6748028` (fix, tests, docs) pushed; **PR rish1789/CMS2#23** opened with the owner's report format. **Not merged.** CI result to be reported from the log.
- [X] T028 Do not run the orphan-token check query (quickstart.md §3) against any real database as part of this feature. Mention it in the PR description as a follow-up decision for the owner.
  - *Result*: not run against any database. It is listed in PR #23 under "What could go wrong" as the owner's decision.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (T001–T002)**: no dependencies.
- **Phase 2 (T003–T011)**: after Phase 1. T003 before T005–T010. T004–T010 in parallel. T011 after all of them. **Blocks Phases 3–6.**
- **Phase 3 (US1, T012–T017)**: after T011. T012 → T013 → (T014, T015) → T016 → T017.
- **Phase 4 (US2)**, **Phase 5 (US3)**, **Phase 6 (US4)**: each after T017, since the shared change lands in US1. They are independent of each other.
- **Phase 7**: T022–T025 can start any time after T011. T026 needs Phases 3–6. T027 needs T026.

### User Story Dependencies

- US1 carries the shared implementation.
- US2, US3 and US4 are verification-only on top of it, except T018's confirmation.
- This is the honest shape of this defect: one root cause, four observable symptoms.

### Parallel Opportunities

- T004–T010: seven test files, all independent.
- T014 and T015: different files, both depend only on T013.
- T019, T020, T021: independent verification runs after T017.
- T022–T025: four independent documentation files.

## Parallel Example: Phase 2

```text
Task: "T004 Tighten QueueSlotIssuanceConcurrencyTest (10 x 20, tomorrow-dated)"
Task: "T005 QueueTokenConcurrentBookingTest (patient + staff mixed, 20 concurrent)"
Task: "T006 WalkInConcurrentRegistrationTest (queue + fixed-time walk-in line)"
Task: "T008 QueueBookingFailureLeavesNoTokenTest (@SpyBean BookingRepository)"
Task: "T009 TokenIssuanceLockTimeoutTest (held lock, 503 within bound)"
Task: "T010 QueueIssuanceRefusalUnderConcurrencyTest"
```

## Implementation Strategy

1. **Phases 1 and 2**: prove the defects exist, with every test red and recorded.
2. **Phase 3 (US1, the MVP)**: the Session lock plus one-transaction queue bookings. This alone fixes all four stories in practice.
3. **Phases 4–6**: verify each remaining story independently.
4. **Phase 7**: documentation corrections, full suite, then a PR for the owner to review. Nothing is merged by the implementer.
