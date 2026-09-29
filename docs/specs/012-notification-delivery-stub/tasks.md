---

description: "Task list for Notification Delivery Stub (Log-Only Send)"
---

# Tasks: Notification Delivery Stub (Log-Only Send)

**Input**: Design documents from `/specs/012-notification-delivery-stub/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/notification-sender.md, quickstart.md

**Tests**: Included — Constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this project.

**Organization**: Single user story (US1 = P1, the feature's entire scope) per spec.md.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

Backend-only per plan.md: `backend/src/main/java/com/cms/notification/` (extends 036's existing module), `backend/src/test/java/com/cms/notification/integration/`.

---

## Phase 1: Setup

**Purpose**: The new event/contract types this feature is built around.

- [X] T001 [P] Create `NotificationEventPublishedEvent` record (`notificationEventId`, `eventType`, `payload`, `pushEligible`, `smsEligible`, `mobile`, `email`, `occurredAt`, plus static `of(NotificationEvent, PatientAccount)`, mirroring `ClinicDeVerifiedEvent`'s shape) in `backend/src/main/java/com/cms/notification/NotificationEventPublishedEvent.java`
- [X] T002 [P] Create `NotificationSender` interface (`send(String channel, String recipient, String message)`) in `backend/src/main/java/com/cms/notification/NotificationSender.java`

**Checkpoint**: Contract types exist; no behavior yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The one additive change to 036's converged service, and the shared test fixture.

**⚠️ CRITICAL**: No user story test can be written until this phase is complete.

- [X] T003 Extend `NotificationEventService.publish()` — inject `ApplicationEventPublisher`, call `eventPublisher.publishEvent(NotificationEventPublishedEvent.of(savedEvent, patientAccount))` immediately after `notificationEventRepository.save(event)`, no other change to its logic/signature — in `backend/src/main/java/com/cms/notification/NotificationEventService.java` (depends on T001)
- [X] T004 Extend `AbstractNotificationIntegrationTest` — register a recording `NotificationSender` test double as a `@TestConfiguration`/`@MockBean`-equivalent bean (capturing every `send(channel, recipient, message)` call for assertions) — in `backend/src/test/java/com/cms/notification/integration/AbstractNotificationIntegrationTest.java` (depends on T002)

**Checkpoint**: Foundation ready — the event now fires on every `publish()` call; User Story 1 can now be built and tested.

---

## Phase 3: User Story 1 - Every Eligible Channel on a Published Event Produces a Verifiable Log Line (Priority: P1) 🎯 MVP

**Goal**: `NotificationDeliveryListener` + `LoggingNotificationSender` turn each eligible channel on a committed `NotificationEvent` into exactly one `send` call, with zero network I/O and zero required configuration, and never fire for a rolled-back publish.

**Independent Test**: Publish events across every eligibility combination and inspect the recording `NotificationSender` test double's captured calls, per quickstart.md Scenarios 1–2, 4.

### Tests for User Story 1 (write first, confirm they FAIL before implementation)

- [X] T005 [P] [US1] Integration test: both channels eligible → `send("push", email, ...)` and `send("sms", mobile, ...)` each called exactly once, in `backend/src/test/java/com/cms/notification/integration/NotificationDeliverySendsOnlyEligibleChannelsTest.java`
- [X] T006 [P] [US1] Integration test (same file as T005, additional `@Test` methods): push-only eligible → only `send("push", ...)` called; neither eligible → `send` never called, in `backend/src/test/java/com/cms/notification/integration/NotificationDeliverySendsOnlyEligibleChannelsTest.java`
- [X] T007 [P] [US1] Integration test: a `publish()` call whose enclosing transaction is rolled back results in zero `send` invocations, in `backend/src/test/java/com/cms/notification/integration/NotificationDeliveryOnlyFiresAfterCommitTest.java`

### Implementation for User Story 1

- [X] T008 [P] [US1] Implement `LoggingNotificationSender implements NotificationSender` — logs one INFO line per call (channel, recipient, message), no other behavior, no injected configuration — in `backend/src/main/java/com/cms/notification/LoggingNotificationSender.java` (depends on T002)
- [X] T009 [US1] Implement `NotificationDeliveryListener` — `@Component`, `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` handler for `NotificationEventPublishedEvent`, calling `notificationSender.send("push", event.email(), message)` iff `pushEligible`, and `notificationSender.send("sms", event.mobile(), message)` iff `smsEligible` — in `backend/src/main/java/com/cms/notification/NotificationDeliveryListener.java` (depends on T001, T002, T003)

**Checkpoint**: User Story 1 (the whole feature) fully functional and independently testable.

---

## Phase 4: Polish & Cross-Cutting Concerns

- [X] T010 Run `quickstart.md` Scenarios 1–4 end-to-end against a real (or Testcontainers) Postgres instance; record pass/fail in `backlog/progress.md`
- [X] T011 Run full backend build (`/tmp/gradle-8.10/bin/gradle build` — use this, not `./gradlew`; `rm -rf backend/build` first if OneDrive sync corruption is suspected); confirm green with zero regressions in 036's existing `publish()`/`markActioned`/`expireDue` tests

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS User Story 1's test-writing.
- **User Story 1 (Phase 3)**: Depends on Foundational.
- **Polish (Phase 4)**: Depends on User Story 1.

### Parallel Opportunities

- T001, T002 in parallel (different files).
- T005–T007 (all US1 tests) in parallel — depend only on T004.
- T008 can start alongside T005–T007 (different file, only depends on T002); T009 depends on T001/T002/T003 together.

---

## Implementation Strategy

### MVP First (and Only) Story

1. Phase 1 → Phase 2 → Phase 3. **STOP and VALIDATE**: quickstart.md Scenarios 1, 2, 4 pass (Scenario 3 — no-config-needed-at-startup — is proven structurally by every other test's Spring context already booting successfully with the new beans present).
2. Phase 4: full-suite verification and quickstart sign-off. This is the feature's entire scope — no further stories.
