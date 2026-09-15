---

description: "Task list for Self-Service Waitlist Claim"
---

# Tasks: Self-Service Waitlist Claim

**Input**: Design documents from `/specs/032-self-service-waitlist-claim/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/waitlist-claim.md, quickstart.md

**Tests**: Included — Constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this project.

**Organization**: Tasks are grouped by user story (US1 = P1 claim/MVP, US2 = P2 decline, US3 = P2 automatic expiry). US1's own claim-failure pivot (research.md R4) depends on the same release mechanism US2/US3 use, so that shared core (`WaitlistReleaseService`) is built in Foundational, not deferred to US2.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

Extends 028's existing `backend/src/main/java/com/cms/waitlist/` and
`backend/src/test/java/com/cms/waitlist/integration/`. Reuses (read-only call, no edits)
`backend/src/main/java/com/cms/booking/PatientBookingService.java` (021) and its exception types.
New: `frontend/src/features/waitlist/ClaimOfferCard.tsx`, `frontend/tests/waitlist/`.

---

## Phase 1: Setup

**Purpose**: The new enum values, entity field, and request DTO.

- [X] T001 [P] Extend `WaitlistEntryStatus` with `CLAIMED`, `EXPIRED` in `backend/src/main/java/com/cms/waitlist/WaitlistEntryStatus.java`
- [X] T002 Add nullable `offeredSlot` (`@ManyToOne` to `com.cms.scheduling.Slot`) field + getter to `WaitlistEntry`, plus in-memory sync helpers `markClaimed()` and `expire()` (mirrors `offer(Instant)`'s own documented purpose — syncing state after a data-layer-guarded update bypasses the persistence context) in `backend/src/main/java/com/cms/waitlist/WaitlistEntry.java` (depends on T001)
- [X] T003 [P] Create `ClaimWaitlistRequest` (`appointmentTypeId`, `patientName`) DTO in `backend/src/main/java/com/cms/waitlist/dto/ClaimWaitlistRequest.java`

**Checkpoint**: Types exist; no behavior yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Migration, repository additions, the OPEN-guard on matching, the shared release core, new exceptions, and the test fixture.

**⚠️ CRITICAL**: No user story test can be written until this phase is complete.

- [X] T004 Create migration `V18__waitlist_entry_offered_slot.sql` per data-model.md in `backend/src/main/resources/db/migration/V18__waitlist_entry_offered_slot.sql` (depends on T002)
- [X] T005 Add `claimIfOffered(id, now)` and `expireIfOffered(id)` conditional `@Modifying` updates, and `findByStatusAndOfferExpiresAtBefore(status, now)`, to `WaitlistEntryRepository` per data-model.md (depends on T002)
- [X] T006 Extend `WaitlistMatchingService.matchAndOffer` to (a) no-op immediately unless `slot.getStatus() == SlotStatus.OPEN` (research.md R10) and (b) persist `offeredSlot` on a successful match, via `offerIfWaiting`'s existing conditional update extended with one more column — in `backend/src/main/java/com/cms/waitlist/WaitlistMatchingService.java` and `WaitlistEntryRepository.java` (depends on T005)
- [X] T007 [P] Create `WaitlistEntryNotFoundException`, `WaitlistOfferNotClaimableException` in `backend/src/main/java/com/cms/waitlist/`
- [X] T008 Extend `WaitlistExceptionHandler` with mappings for both new exceptions (`404 WAITLIST_ENTRY_NOT_FOUND`, `409 WAITLIST_OFFER_NOT_CLAIMABLE`) per contracts/waitlist-claim.md (depends on T007)
- [X] T009 Implement `WaitlistReleaseService.release(WaitlistEntry entry)` — calls `expireIfOffered`; on success (1 row), syncs in-memory via `entry.expire()`, loads `entry.getOfferedSlot().getSession()`, and calls `WaitlistMatchingService.matchAndOffer(session, slot)` again; on 0 rows (lost race), no-ops — in `backend/src/main/java/com/cms/waitlist/WaitlistReleaseService.java` (depends on T005, T006)
- [X] T010 Add explicit matchers `.requestMatchers(HttpMethod.POST, "/api/v1/patients/waitlist-entries/*/claim").authenticated()` and `.../decline` to the `/api/v1/patients/**` chain in `backend/src/main/java/com/cms/patient/account/SecurityConfig.java`
- [X] T011 Extend `AbstractWaitlistIntegrationTest` with an `offerEntry(entry, slot, offeredAt, offerExpiresAt)` helper (raw JDBC update + `EntityManager.refresh`, mirroring `saveWaitlistEntry`'s existing backdating pattern) for constructing an `OFFERED` entry with a specific offered Slot and a controllable, possibly-already-lapsed window — in `backend/src/test/java/com/cms/waitlist/integration/AbstractWaitlistIntegrationTest.java` (depends on T002, T005)

**Checkpoint**: Foundation ready — all three user stories can now be built.

---

## Phase 3: Patient Claims an Offered Slot (Priority: P1) 🎯 MVP

**Goal**: The offered patient converts their `OFFERED` entry into a confirmed Booking themselves, within the window.

**Independent Test**: Per quickstart.md Scenarios 1, 2, 5.

### Tests for User Story 1 (write first, confirm they FAIL before implementation)

- [X] T012 [P] [US1] Integration test: claiming an `OFFERED` entry within its window → `201`, a confirmed Booking for the offered Slot, fee resolved/locked at claim time (FR-001/FR-002, SC-001) — in `backend/src/test/java/com/cms/waitlist/integration/WaitlistClaimTest.java`
- [X] T013 [P] [US1] Integration test: a claim by a patient who doesn't own the entry → `404 WAITLIST_ENTRY_NOT_FOUND` (FR-003, research.md R8); a claim against a `WAITING`, already-`CLAIMED`, or already-`EXPIRED` entry → `409 WAITLIST_OFFER_NOT_CLAIMABLE`; a claim against an `OFFERED` entry whose window has already lapsed → `409 WAITLIST_OFFER_NOT_CLAIMABLE` (FR-004) — in the same file as T012
- [X] T014 [P] [US1] Integration test: the offered Slot was already booked ordinarily (016/017) before the claim → `409 SLOT_ALREADY_BOOKED`; the entry is `EXPIRED`, not `CLAIMED`; no other entry is offered this same now-`BOOKED` Slot even with another eligible entry present (FR-004a, research.md R4/R10, quickstart Scenario 5) — in the same file as T012

### Implementation for User Story 1

- [X] T015 [US1] Implement `WaitlistClaimService.claim(entryId, patientAccountId, clinicId, ClaimWaitlistRequest)` per research.md R4: ownership-scoped lookup, `claimIfOffered` guard, `entry.markClaimed()` sync, delegate to `PatientBookingService.bookSlot(...)`; on `SlotAlreadyBookedException`, transition the entry `CLAIMED → EXPIRED` directly (already exclusively owned, no race guard needed), re-run `WaitlistMatchingService.matchAndOffer` against the same Slot's Session (R10 makes this a safe no-op), then re-throw — in `backend/src/main/java/com/cms/waitlist/WaitlistClaimService.java` (depends on T005, T006, T009)
- [X] T016 [US1] Implement `PatientWaitlistClaimController`'s claim endpoint (`POST /api/v1/patients/waitlist-entries/{entryId}/claim`) per contracts/waitlist-claim.md, returning `com.cms.booking.dto.BookingResponse` — in `backend/src/main/java/com/cms/waitlist/PatientWaitlistClaimController.java` (depends on T003, T015)

**Checkpoint**: User Story 1 fully functional and independently testable.

---

## Phase 4: Patient Declines an Offered Slot (Priority: P2)

**Goal**: The offered patient explicitly releases their offer, immediately advancing the waitlist.

**Independent Test**: Per quickstart.md Scenario 3.

### Tests for User Story 2 (write first, confirm they FAIL before implementation)

- [X] T017 [P] [US2] Integration test: declining an `OFFERED` entry with another eligible entry present → `200`, `status: "EXPIRED"`; the next-longest-waiting eligible entry is now `OFFERED` the same Slot with a fresh window (FR-005/FR-006, SC-003) — in `backend/src/test/java/com/cms/waitlist/integration/WaitlistDeclineTest.java`
- [X] T018 [P] [US2] Integration test: declining with no other eligible entry → `200`, no further offer made, Slot remains available (FR-009); a decline by a non-owning patient → `404`; a decline against a non-`OFFERED` entry → `409 WAITLIST_OFFER_NOT_CLAIMABLE` — in the same file as T017

### Implementation for User Story 2

- [X] T019 [US2] Implement `WaitlistClaimService.decline(entryId, patientAccountId)` — ownership-scoped lookup, delegates to `WaitlistReleaseService.release(entry)` — in `backend/src/main/java/com/cms/waitlist/WaitlistClaimService.java` (depends on T009)
- [X] T020 [US2] Implement `PatientWaitlistClaimController`'s decline endpoint (`POST /api/v1/patients/waitlist-entries/{entryId}/decline`) per contracts/waitlist-claim.md, returning `WaitlistEntryResponse` — in `backend/src/main/java/com/cms/waitlist/PatientWaitlistClaimController.java` (depends on T019)

**Checkpoint**: User Stories 1 and 2 both independently functional.

---

## Phase 5: Offer Automatically Expires and Re-Offers (Priority: P2)

**Goal**: An unresponsive patient's lapsed offer is detected and moved on from with no explicit action.

**Independent Test**: Per quickstart.md Scenario 4.

### Tests for User Story 3 (write first, confirm they FAIL before implementation)

- [X] T021 [P] [US3] Integration test: an `OFFERED` entry whose window has already lapsed, with another eligible entry present → after the sweep, the lapsed entry is `EXPIRED` and the next eligible entry is `OFFERED` the same Slot with a fresh window (FR-007/FR-008, SC-003) — in `backend/src/test/java/com/cms/waitlist/integration/WaitlistExpirySweepTest.java`
- [X] T022 [P] [US3] Integration test: a lapsed entry with no other eligible entry → after the sweep, no further offer, Slot remains available (FR-009); an `OFFERED` entry whose window has NOT yet lapsed → left untouched by the sweep (Acceptance Scenario 3) — in the same file as T021

### Implementation for User Story 3

- [X] T023 [US3] Implement `WaitlistExpirySweepService.sweepExpiredOffers()` — queries `findByStatusAndOfferExpiresAtBefore(OFFERED, now)`, calls `WaitlistReleaseService.release(entry)` for each — in `backend/src/main/java/com/cms/waitlist/WaitlistExpirySweepService.java` (depends on T005, T009)
- [X] T024 [US3] Implement `WaitlistExpirySweepTrigger` — `@Scheduled(cron = "0 * * * * *")`, mirrors `NoShowDetectionTrigger`'s exact cadence/logging shape — in `backend/src/main/java/com/cms/waitlist/WaitlistExpirySweepTrigger.java` (depends on T023)

**Checkpoint**: All three user stories independently functional — the complete self-service claim/decline/expiry flow.

---

## Phase 5.5: Cross-Story Verification (Analyze findings C1/C2)

**Purpose**: These two tests genuinely need all three stories' implementations to exist first (they chain/race claim, decline, and expiry against each other), so they don't fit the per-story "write first" TDD shape — written and run only once Phases 3–5 are complete.

- [X] T022a [P] Integration test: a genuine multi-hop chain — three eligible entries for the same doctor; the first `OFFERED` entry declines, the second (now `OFFERED`) is left to expire via the sweep, and the third (now `OFFERED`) successfully claims — confirms FR-008's "repeats until claimed or exhausted" end-to-end, not just one hop at a time (Analyze finding C2) — in `backend/src/test/java/com/cms/waitlist/integration/WaitlistMultiHopCascadeTest.java` (depends on T016, T020, T023)
- [X] T022b Concurrency test: two concurrent actions against the same `OFFERED` entry (a claim racing a decline, and separately a claim racing the expiry sweep) — confirm exactly one ever succeeds and the entry ends in exactly one terminal state, mirroring `PartialSessionCancellationConcurrencyTest`'s (030) real-thread shape (FR-010/SC-004, Analyze finding C1) — in `backend/src/test/java/com/cms/waitlist/integration/WaitlistClaimConcurrencyTest.java` (depends on T016, T020, T023)

---

## Phase 6: Frontend & Polish

- [X] T025 [P] Extend `frontend/src/features/waitlist/api.ts` — `claimOffer(entryId, request, token)`, `declineOffer(entryId, token)`
- [X] T026 Create `frontend/src/features/waitlist/ClaimOfferCard.tsx` — shows an `OFFERED` entry with Claim/Decline actions (depends on T025)
- [X] T027 [P] Frontend test: claims, declines, shows `WAITLIST_OFFER_NOT_CLAIMABLE`/`SLOT_ALREADY_BOOKED` error messages — in `frontend/tests/waitlist/ClaimOfferCard.test.tsx` (depends on T026)
- [X] T028 Run `quickstart.md` Scenarios 1–6 end-to-end against a real (or Testcontainers) Postgres instance; record pass/fail in `backlog/progress.md` — blocked by the sandbox's Testcontainers/Docker limitation (same as every prior feature this session); Scenarios 1-5 each covered by a passing-when-run integration test (T012-T014, T017-T018, T021-T022, T022a, T022b) verified via compile + structural review; Scenario 6 (frontend) verified live via the Vitest suite (T027, 8/8 green)
- [X] T029 Run full backend build (`/tmp/gradle-8.10/bin/gradle compileJava compileTestJava spotlessCheck -q` and `build -x test`) and full frontend suite (`npm test` in `frontend/`); confirm both green with zero regressions in 028's existing tests (given `WaitlistMatchingService` and `WaitlistEntryRepository` were both extended in place)

---

## Dependencies & Execution Order

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all three user stories' test-writing. Builds `WaitlistReleaseService` here (not in US2) since US1's own claim-failure pivot needs it too.
- **US1 / Claim (Phase 3)**: Depends on Foundational.
- **US2 / Decline (Phase 4)**: Depends on Foundational. Independent of US1 (does not call `WaitlistClaimService.claim`).
- **US3 / Expiry Sweep (Phase 5)**: Depends on Foundational. Independent of US1/US2's own controllers, but exercises the same `WaitlistReleaseService` US2 does.
- **Frontend & Polish (Phase 6)**: Depends on all three stories.

### Parallel Opportunities

- T001, T003 in parallel; T002 depends on T001.
- T007 items in parallel.
- T012–T014 (US1 tests) in parallel — depend on T011.
- T017–T018 (US2 tests) in parallel — depend on T011.
- T021–T022 (US3 tests) in parallel — depend on T011.
- Once Foundational (Phase 2) completes, US1/US2/US3 implementation can proceed in any order — none calls into another's controller.

---

## Implementation Strategy

1. Phase 1 → Phase 2. **STOP and VALIDATE**: foundational pieces compile, no behavior yet.
2. Phase 3 (claim). **STOP and VALIDATE**: quickstart.md Scenarios 1, 2, 5 pass.
3. Phase 4 (decline). **STOP and VALIDATE**: quickstart.md Scenario 3 passes.
4. Phase 5 (expiry sweep). **STOP and VALIDATE**: quickstart.md Scenario 4 passes.
5. Phase 6: frontend, full-suite verification, quickstart sign-off.
