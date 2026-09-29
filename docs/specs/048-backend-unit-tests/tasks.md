---

description: "Task list for Backend Unit-Test Backfill"
---

# Tasks: Backend Unit-Test Backfill

**Input**: Design documents from `/specs/048-backend-unit-tests/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md (N/A), quickstart.md

**Tests**: This feature IS test-writing — no separate "tests for the tests."

**Organization**: One phase per targeted class (= one user story each), fully independent.

## Phase 1-2: Setup / Foundational

Not needed.

---

## Phase 3: User Story 1 - FeeResolutionService (Priority: P1) 🎯 MVP

- [X] T001 [US1] Created `FeeResolutionServiceTest.java` (mocked repositories + entities per research.md Decision 1). 4 tests, all pass.
- [X] T002 [US1] Verified per `quickstart.md` step 2 — green.

---

## Phase 4: User Story 2 - BookingCancellationService (Priority: P1)

- [X] T003 [US2] Created `BookingCancellationServiceTest.java`. 4 tests, all pass (the success-path test uses an `ArgumentCaptor` on the published event, comparing only `bookingId`/`slotId` — the event's own `occurredAt` field is a real `Instant.now()` call inside production code, not equality-comparable against a separately-constructed expected instance).
- [X] T004 [US2] Verified per `quickstart.md` step 2 — green.

---

## Phase 5: User Story 3 - ScheduleService validation + overlap (Priority: P2)

- [X] T005 [US3] Created `ScheduleServiceTest.java` (mocked `EntityManager`/`Query` per research.md Decision 2). 10 tests: the 4 validation rules individually, authorization forbidden, doctor-not-staffed, shared-day+overlap rejected, touching-boundary accepted, different-day accepted, edit-self-exclusion accepted — all pass. Hit and fixed 2 real `UnnecessaryStubbingException`s during writing (Mockito's strict-stubs mode correctly caught two stubs that didn't match the actual code path: a `findByDoctorProfile_Id` stub the not-staffed-rejection path never reaches since it throws earlier, and a `grantDoctorStaffed()` helper call in the edit test — `edit()` has no doctor-staffed gate at all, only `create()` does) — both removed, not worked around.
- [X] T006 [US3] Verified per `quickstart.md` step 2 — green.

---

## Phase 6: User Story 4 - NoShowDetectionService (Priority: P2)

- [X] T007 [US4] Created `NoShowDetectionServiceTest.java` (relative-to-real-now scheduled times per research.md Decision 3: 2 minutes ago for within-grace, 2 hours ago for past-grace). 2 tests, both pass.
- [X] T008 [US4] Verified per `quickstart.md` step 2 — green.

---

## Phase 7: Polish

- [X] T009 Ran `quickstart.md` steps 1-4 in full: `spotlessCheck compileJava compileTestJava` clean; full Docker-independent subset (now 6 module groups: identity contract, discovery unit, notification unit, booking unit, scheduling unit) all green, 26 total tests; full backend compile (including the Testcontainers-backed integration suite) clean.
- [X] T010 Updated `backlog/progress.md`'s row for `045-backend-unit-test-backfill`.

---

## Dependencies & Execution Order

- All 4 user stories are fully independent (different files, different modules even within booking/scheduling) — any order.
- Polish depends on all 4 being complete.

## Notes

- Total: 10 tasks.
- If any test reveals a genuine bug in the class under test (FR-006), report it explicitly before treating that task as done — do not silently work around it.
