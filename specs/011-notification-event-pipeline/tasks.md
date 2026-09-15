---

description: "Task list for Notification Event Pipeline & Opt-In/Out"
---

# Tasks: Notification Event Pipeline & Opt-In/Out

**Input**: Design documents from `/specs/011-notification-event-pipeline/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/notification-event-service.md, quickstart.md

**Tests**: Included — Constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this project.

**Organization**: Tasks are grouped by user story (US1 = P1 publish/eligibility, US2 = P2 expiry lifecycle) per spec.md.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

Backend-only per plan.md (no frontend surface): `backend/src/main/java/com/cms/notification/`, `backend/src/main/java/com/cms/patient/account/` (extended), `backend/src/test/java/com/cms/notification/integration/`.

---

## Phase 1: Setup

**Purpose**: Schema and entity scaffolding shared by both stories.

- [X] T001 Create migration `V6__notification_event_pipeline.sql` — `ALTER TABLE patient_account ADD COLUMN sms_opt_in BOOLEAN NOT NULL DEFAULT true, ADD COLUMN push_opt_in BOOLEAN NOT NULL DEFAULT true;` and `CREATE TABLE notification_event (...)` per data-model.md, plus a composite index `idx_notification_event_status_expires_at ON notification_event (status, expires_at)` backing `expireDue`'s bulk-update predicate (plan.md Performance Goals) — in `backend/src/main/resources/db/migration/V6__notification_event_pipeline.sql`
- [X] T002 Extend `PatientAccount` — add `smsOptIn`/`pushOptIn` fields (default `true`), `isSmsOptIn()`/`setSmsOptIn(boolean)`, `isPushOptIn()`/`setPushOptIn(boolean)`; leave `notificationOptIn` untouched (research.md) — in `backend/src/main/java/com/cms/patient/account/PatientAccount.java` (depends on T001)
- [X] T003 [P] Create `NotificationEventStatus` enum (`PENDING`, `ACTIONED`, `EXPIRED`) in `backend/src/main/java/com/cms/notification/NotificationEventStatus.java`
- [X] T004 [P] Create `PatientAccountNotFoundException`, `NotificationEventNotFoundException`, `NotificationEventAlreadyExpiredException` in `backend/src/main/java/com/cms/notification/`
- [X] T005 Create `NotificationEvent` entity (fields per data-model.md: id, patientAccount, eventType, payload, pushEligible, smsEligible, expiresAt, status, createdAt) in `backend/src/main/java/com/cms/notification/NotificationEvent.java` (depends on T001, T003)

**Checkpoint**: Schema and entities exist; no behavior yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Repository and shared test fixture both stories build on.

**⚠️ CRITICAL**: No user story test can be written until this phase is complete.

- [X] T006 Create `NotificationEventRepository` (extends `JpaRepository<NotificationEvent, UUID>`, plus a `@Modifying @Query` bulk-update method for the `expireDue` predicate — `status = PENDING AND expiresAt IS NOT NULL AND expiresAt < :now`, per research.md's conditional-update design) in `backend/src/main/java/com/cms/notification/NotificationEventRepository.java` (depends on T005)
- [X] T007 Create `AbstractNotificationIntegrationTest.java` — Testcontainers Postgres base class with helper builders for a `PatientAccount` (with configurable `smsOptIn`/`pushOptIn`/`mobile`) and direct `NotificationEventRepository` access for test setup/assertions — in `backend/src/test/java/com/cms/notification/integration/AbstractNotificationIntegrationTest.java` (depends on T002, T006)

**Checkpoint**: Foundation ready — User Story 1 can now be built.

---

## Phase 3: User Story 1 - A Future Feature Publishes a Notification Event Without Waiting on Delivery (Priority: P1) 🎯 MVP

**Goal**: `NotificationEventService.publish(...)` creates a `NotificationEvent` with correctly computed, snapshotted channel eligibility, and returns with no delivery dependency.

**Independent Test**: Call `publish` directly for patients across every combination of `smsOptIn`/`pushOptIn`/mobile-present; assert the returned/persisted event's `pushEligible`/`smsEligible` match expectations exactly, per quickstart.md Scenarios 1, 2, 6.

### Tests for User Story 1 (write first, confirm they FAIL before implementation)

- [X] T008 [P] [US1] Integration test: both channels opted in + mobile present → both eligible, in `backend/src/test/java/com/cms/notification/integration/PublishChannelEligibilityTest.java`
- [X] T009 [P] [US1] Integration test (same file as T008, additional `@Test` methods): SMS opted out → `smsEligible=false`, push unaffected; both opted out → both `false` but event still created, in `backend/src/test/java/com/cms/notification/integration/PublishChannelEligibilityTest.java`
- [X] T010 [P] [US1] Integration test: `smsOptIn=true` but no mobile on file → `smsEligible=false` regardless, in `backend/src/test/java/com/cms/notification/integration/PublishNoPhoneNumberSmsIneligibleTest.java`
- [X] T011 [P] [US1] Integration test: a later change to the patient's opt-in flags does not retroactively change an already-published event's recorded eligibility (the snapshot decision, FR-002), in `backend/src/test/java/com/cms/notification/integration/PublishChannelEligibilityTest.java`
- [X] T012 [P] [US1] Integration test: `publish` throws `PatientAccountNotFoundException` for an unknown patient id; `get` throws `NotificationEventNotFoundException` for an unknown event id, in `backend/src/test/java/com/cms/notification/integration/PublishAccountAndEventNotFoundTest.java`

### Implementation for User Story 1

- [X] T013 [US1] Implement `NotificationEventService.publish(UUID patientAccountId, String eventType, String payload, Instant expiresAt)` and `.get(UUID eventId)` per data-model.md/contracts — in `backend/src/main/java/com/cms/notification/NotificationEventService.java` (depends on T006, T007)

**Checkpoint**: User Story 1 fully functional and independently testable — events can be published with correct, immutable eligibility snapshots.

---

## Phase 4: User Story 2 - A Time-Boxed Event Automatically Expires If Never Actioned (Priority: P2)

**Goal**: `markActioned` and `expireDue` implement the generic notify-once/auto-expire lifecycle.

**Independent Test**: Publish time-boxed events, exercise the actioned/expired paths and the sweep's idempotency, per quickstart.md Scenarios 3, 4, 5.

### Tests for User Story 2 (write first, confirm they FAIL before implementation)

- [X] T014 [P] [US2] Integration test: a lapsed, unactioned event is `EXPIRED` after `expireDue()`, in `backend/src/test/java/com/cms/notification/integration/ExpireDueSweepTest.java`
- [X] T015 [P] [US2] Integration test: calling `expireDue()` a second time makes no further change and returns `0`, in `backend/src/test/java/com/cms/notification/integration/ExpireDueIdempotentTest.java`
- [X] T016 [P] [US2] Integration test: a `PENDING` event with `expiresAt = null` is untouched by any number of `expireDue()` calls, in `backend/src/test/java/com/cms/notification/integration/ExpireDueLeavesNonTimeBoxedEventsAloneTest.java`
- [X] T017 [P] [US2] Integration test: an event `markActioned` before its window lapses stays `ACTIONED` (not `EXPIRED`) after a subsequent `expireDue()`; its remaining window is queryable before expiry, in `backend/src/test/java/com/cms/notification/integration/MarkActionedProtectsFromExpiryTest.java`
- [X] T018 [P] [US2] Integration test: `markActioned` on an already-`EXPIRED` event throws `NotificationEventAlreadyExpiredException` and leaves its status `EXPIRED`, in `backend/src/test/java/com/cms/notification/integration/MarkActionedOnExpiredEventRejectedTest.java`

### Implementation for User Story 2

- [X] T019 [US2] Implement `NotificationEventService.markActioned(UUID eventId)` and `.expireDue()` per data-model.md/contracts — in `backend/src/main/java/com/cms/notification/NotificationEventService.java` (depends on T013)

**Checkpoint**: Both user stories independently functional — the full generic publish + notify-once/auto-expire pipeline is complete.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [X] T020 Run `quickstart.md` Scenarios 1–6 end-to-end against a real (or Testcontainers) Postgres instance; record pass/fail in `backlog/progress.md`
- [X] T021 Run full backend build (`/tmp/gradle-8.10/bin/gradle build` — use this, not `./gradlew`; `rm -rf backend/build` first if OneDrive sync corruption is suspected); confirm green with zero regressions in prior features' tests (including 039's `PatientSignupHappyPathTest`, given `PatientAccount` was extended)

---

## Phase 6: Convergence

- [X] T022 Close a lost-update race in `NotificationEventService.markActioned()`: replace its read-then-check-then-write with a data-layer-guarded conditional update (a new `@Modifying @Query("UPDATE NotificationEvent e SET e.status = ACTIONED WHERE e.id = :id AND e.status = PENDING")` on `NotificationEventRepository`, mirroring `expireDuePending`'s existing design), so a concurrent `expireDue()` sweep can never be silently overwritten back to `ACTIONED` — in `backend/src/main/java/com/cms/notification/NotificationEventRepository.java` and `NotificationEventService.java` per Constitution IV (contradicts)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS both user stories' test-writing.
- **User Story 1 (Phase 3)**: Depends on Foundational. No dependency on US2.
- **User Story 2 (Phase 4)**: Depends on US1's `publish`/`get` existing (its tests need a way to create the events they then expire/action) — not independently buildable from a blank slate, but independently testable once built.
- **Polish (Phase 5)**: Depends on both stories.

### Parallel Opportunities

- T003, T004 in parallel (different files).
- T008–T012 (all US1 tests) in parallel — depend only on T007.
- T014–T018 (all US2 tests) in parallel — depend on T013 (US1's implementation) being in place.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 → Phase 2 → Phase 3 (US1). **STOP and VALIDATE**: quickstart.md Scenarios 1, 2, 6 pass. This alone delivers the core decoupled-publish + eligibility guarantee.

### Incremental Delivery

2. Add Phase 4 (US2): quickstart.md Scenarios 3, 4, 5 pass. Delivers the full notify-once/auto-expire lifecycle.
3. Phase 5: full-suite verification and quickstart sign-off.
