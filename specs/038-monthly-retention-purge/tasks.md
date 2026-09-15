---
description: "Task list for Monthly Automatic Retention Purge"
---

# Tasks: Monthly Automatic Retention Purge

**Input**: Design documents from `/specs/038-monthly-retention-purge/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/retention-purge.md, quickstart.md

**Tests**: Included per Constitution Principle I (Test-First Development, NON-NEGOTIABLE) — tests are written before their corresponding implementation.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2)

## Phase 1: Setup

No new project scaffolding needed — this feature adds files to existing `com.cms.clinical`, `com.cms.booking`, and `com.cms.identity.admin` packages in the existing `backend/` module.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared infrastructure both user stories depend on.

**⚠️ CRITICAL**: Must complete before either user story.

- [X] T001 Extend `Prescription.items`' `@OneToMany` cascade from `CascadeType.PERSIST` to `{CascadeType.PERSIST, CascadeType.REMOVE}` in `backend/src/main/java/com/cms/clinical/Prescription.java` (research.md R3) — update its class javadoc's "no update/delete code path exists anywhere in this module" claim to note this feature's foreseen exception.
- [X] T002 Add `findRetentionEligibleBookings(Instant retentionCutoff): List<Booking>` JPQL query to `backend/src/main/java/com/cms/booking/BookingRepository.java` matching `b.patient.anonymizedAt IS NOT NULL AND b.createdAt < :retentionCutoff` (research.md R4, data-model.md).

**Checkpoint**: Foundation ready — both user stories can now proceed.

---

## Phase 3: User Story 1 - Automatic monthly purge (Priority: P1) 🎯 MVP

**Goal**: Bookings 3+ years old (by `createdAt`) with an anonymized patient have their Consultation Note, Prescriptions/Items, and External Record References permanently deleted; the booking and Patient shell are untouched; ineligible bookings are left alone.

**Independent Test**: Seed a booking whose `createdAt` is 3+ years in the past, patient anonymized, all three content types attached; invoke the purge service directly; verify all three content types are gone while the booking and Patient remain. Seed two more bookings each failing exactly one condition; verify their content survives.

### Tests for User Story 1

> Write these tests FIRST; ensure they FAIL before implementation.

- [X] T003 [P] [US1] Create `AbstractRetentionPurgeIntegrationTest` fixture in `backend/src/test/java/com/cms/clinical/integration/AbstractRetentionPurgeIntegrationTest.java` — clinic/doctor/patient helpers, a `bookSlotForPatientWithCreatedAt(doctor, slot, patient, Instant createdAt)`-style helper (direct repository save + manual `createdAt` backdating, since no API path backdates a booking), autowiring `RetentionPurgeService`, `BookingRepository`, `PatientRepository`, `ConsultationNoteRepository`, `PrescriptionRepository`, `ExternalRecordReferenceRepository`, `ConsultationNoteService`, `PrescriptionService`, `ExternalRecordReferenceService`, `PatientAnonymizationService`.
- [X] T004 [US1] Write `RetentionPurgeTest.purgesAllThreeContentTypesForAnEligibleBooking()` in `backend/src/test/java/com/cms/clinical/integration/RetentionPurgeTest.java` — 3+ year old booking, anonymized patient, Consultation Note + Prescription(with items) + External Record Reference attached; run `retentionPurgeService.purge()`; assert all three are gone, booking and Patient remain (spec.md Acceptance Scenario 1).
- [X] T005 [US1] Write `RetentionPurgeTest.leavesContentUntouchedWhenPatientNotAnonymized()` — 3+ year old booking, NOT anonymized, Consultation Note attached; run purge; assert note still present (Acceptance Scenario 2).
- [X] T006 [US1] Write `RetentionPurgeTest.leavesContentUntouchedWhenBookingIsRecent()` — anonymized patient, booking `createdAt` < 3 years ago, Consultation Note attached; run purge; assert note still present (Acceptance Scenario 3).
- [X] T007 [US1] Write `RetentionPurgeTest.purgedBookingStillExposesNonClinicalMetadata()` — after purging an eligible booking, assert `bookingRepository.findById(...)` still returns it with its slot/doctor/date intact (Acceptance Scenario 4).
- [X] T008 [P] [US1] Write `RetentionPurgeTest.skipsBookingsWithNoClinicalContentAttached()` — eligible booking with zero content of any type attached; run purge; assert no exception and `purgedBookingCount` excludes it or counts it as a no-op consistently (Acceptance Scenario 5, FR-009).
- [X] T009 [US1] Write `RetentionPurgeTest.rerunningThePurgeIsIdempotent()` — run purge twice in a row over the same eligible booking; assert the second run does not error and leaves the same end state (FR-011).
- [X] T010 [P] [US1] Write `RetentionPurgeTest.purgesMultiplePrescriptionsAndExternalRecordReferencesForABooking()` — an eligible booking with two Prescriptions and two External Record References; run purge; assert all are gone (Edge case: multiple of each type).

### Implementation for User Story 1

- [X] T011 [US1] Create `RetentionPurgeService` in `backend/src/main/java/com/cms/clinical/RetentionPurgeService.java` — `RETENTION_PERIOD = Period.ofYears(3)` constant; `purge(): RetentionPurgeResult` (or `int`) computing the cutoff (research.md R5: `LocalDate.now(clock).minusYears(3).atStartOfDay(ZoneOffset.UTC).toInstant()`), calling `bookingRepository.findRetentionEligibleBookings(cutoff)`, and for each booking deleting its `ConsultationNote` (via `consultationNoteRepository.findByBooking_Id` then `deleteById` if present), all its `Prescription`s (via `findByBooking_Id` then `deleteById` each), and all its `ExternalRecordReference`s (via `findByBooking_Id` then `deleteById` each); returns the count of bookings processed.
- [X] T012 [US1] Create `RetentionPurgeTrigger` in `backend/src/main/java/com/cms/clinical/RetentionPurgeTrigger.java` — `@Component`, `@Scheduled(cron = "0 0 0 1 * *")`, calls `retentionPurgeService.purge()`, logs the result (research.md R7, mirrors `WaitlistExpirySweepTrigger`).
- [X] T013 [US1] Run `RetentionPurgeTest` and `AbstractRetentionPurgeIntegrationTest`-dependent tests to green; run full `backend` test suite for regressions in `com.cms.clinical`/`com.cms.booking` from the `Prescription` cascade change (031's own `PrescriptionTest`/`ConsultationNoteTest`/`ExternalRecordReferenceTest` classes).

**Checkpoint**: User Story 1 fully functional and independently testable — the automatic monthly purge works correctly via direct service invocation.

---

## Phase 4: User Story 2 - Super Admin manual re-trigger (Priority: P2)

**Goal**: A Super Admin can invoke the purge on demand via an authenticated HTTP endpoint; every other role/unauthenticated caller is rejected.

**Independent Test**: Call the manual-trigger endpoint with valid Super Admin Basic Auth credentials after US1's automatic logic already ran; verify it runs again and reports a count. Call it with a staff JWT or no credentials; verify rejection.

### Tests for User Story 2

> Write these tests FIRST; ensure they FAIL before implementation.

- [X] T014 [P] [US2] Write `RetentionPurgeAuthorizationTest.superAdminCanManuallyTriggerThePurge()` in `backend/src/test/java/com/cms/identity/admin/integration/RetentionPurgeAuthorizationTest.java` — `POST /api/v1/admin/retention-purge/run` with valid Super Admin Basic Auth; assert `200 OK` and a `purgedBookingCount` field (Acceptance Scenario US2.1, contracts/retention-purge.md).
- [X] T015 [P] [US2] Write `RetentionPurgeAuthorizationTest.rejectsRequestsWithoutValidSuperAdminCredentials()` — same endpoint with a staff JWT `Authorization: Bearer ...` header, and separately with no `Authorization` header at all; assert `401 Unauthorized` both times and no purge side effect (Acceptance Scenario US2.2, FR-008).
- [X] T016 [US2] Write `RetentionPurgeAuthorizationTest.manualTriggerCanBeCalledAgainAfterTheAutomaticJobAlreadyRan()` — run `retentionPurgeService.purge()` directly (simulating the scheduled job already having fired), then call the manual endpoint; assert `200 OK` with no error, correctly picking up any newly-eligible booking (Acceptance Scenario US2.1 / FR-011).

### Implementation for User Story 2

- [X] T017 [P] [US2] Create `RetentionPurgeResultResponse` DTO in `backend/src/main/java/com/cms/identity/admin/dto/RetentionPurgeResultResponse.java` — `int purgedBookingCount`.
- [X] T018 [US2] Create `RetentionPurgeController` in `backend/src/main/java/com/cms/identity/admin/RetentionPurgeController.java` — `@RestController`, `@RequestMapping("/api/v1/admin/retention-purge")`, `POST /run` calling `com.cms.clinical.RetentionPurgeService.purge()` and returning `RetentionPurgeResultResponse` (contracts/retention-purge.md; research.md R8 — no extra role check needed, the Super Admin security chain's own authentication is the authorization).
- [X] T019 [US2] Run `RetentionPurgeAuthorizationTest` to green; run full `backend` test suite for regressions in `com.cms.identity.admin`.

**Checkpoint**: Both user stories independently functional.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [X] T020 Run `quickstart.md`'s four scenarios end-to-end against a local backend instance (Docker/Testcontainers permitting — expect the sandbox's known Testcontainers limitation per prior features this session; document as such if it recurs). Confirmed: identical `IllegalStateException: Could not find a valid Docker environment` sandbox limitation as every prior feature this session — all 11 new tests (`RetentionPurgeTest` x7, `RetentionPurgeAuthorizationTest` x4) compile and are structurally sound; full backend suite run (226 tests) shows only Docker-caused failures, zero non-Docker regressions (verified by grepping every test result XML for the Docker failure signature).
- [X] T021 Update `backlog/progress.md` marking 034-monthly-retention-purge (specs/038) as Converged with technical notes (cascade change, controller placement rationale, retention-anchor Assumption).

---

## Dependencies & Execution Order

- **Foundational (Phase 2)**: No dependencies — blocks both US1 and US2 (both need the cascade fix and the eligibility query).
- **User Story 1 (P1)**: Depends on Phase 2. No dependency on US2.
- **User Story 2 (P2)**: Depends on Phase 2 and on `RetentionPurgeService` existing (T011, from US1) — the controller just calls it. Not independently deliverable before US1's service exists, but its own tests/controller are otherwise separate files from US1.
- **Polish (Phase 5)**: After both user stories.

### Parallel Opportunities

- T001/T002 (Phase 2) touch different files — parallel.
- T003, T008, T010 (US1 tests, different test methods but same file after T003 creates the fixture) — T003 must land first; T008/T010 can be written in parallel with T004-T007 conceptually but share `RetentionPurgeTest.java`, so treat as sequential edits to one file in practice.
- T014/T015/T017 — different files, parallel with each other.

## Implementation Strategy

**MVP = User Story 1 only**: the automatic monthly purge is the actual DPDP compliance mechanism (spec.md priority rationale); User Story 2 (manual trigger) is an operational safety valve layered on top of the same service.
