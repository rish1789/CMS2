# Implementation Plan: Notification Delivery Stub (Log-Only Send)

**Branch**: `012-notification-delivery-stub` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/012-notification-delivery-stub/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Extends 036's `NotificationEventService.publish()` with one additive line — `eventPublisher.publishEvent(NotificationEventPublishedEvent.of(savedEvent))` after the event is saved — mirroring this codebase's own `ClinicDeVerifiedEvent` precedent (003 publishes, 008 later listens) exactly. This feature is the "008" half of that same shape for 036: a new `@Component` listener, `@TransactionalEventListener(phase = AFTER_COMMIT)`, reads the event's channel eligibility and calls a small `NotificationSender` interface once per eligible channel; the only v1 implementation, `LoggingNotificationSender`, just logs. No network client, no configuration, no delivery-status tracking anywhere.

## Technical Context

**Language/Version**: Java 21 (backend-only — no frontend surface; nothing user-facing to build).

**Primary Dependencies**: Spring Boot 3.x (context events, `ApplicationEventPublisher`/`@TransactionalEventListener`) — reuses `com.cms.notification.NotificationEvent`/`NotificationEventService` (036) directly, extending 036's own service. No new external dependency.

**Storage**: None — this feature adds no table, no column, no migration.

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers (backend only — the DB is still needed to exercise 036's `publish()`, which this feature's tests drive to trigger the listener).

**Target Platform**: Linux container (Docker).

**Project Type**: Backend service module (no web/API layer).

**Performance Goals**: Negligible — a log statement, no I/O.

**Constraints**:
- The new listener MUST be `@TransactionalEventListener(phase = AFTER_COMMIT)`, not a plain `@EventListener` — firing only after 036's `publish()` transaction actually commits is what closes FR-005/SC-004 (never logging a phantom send for a rolled-back publish).
- `NotificationSender` MUST be an interface with exactly one method (channel, recipient, message) and exactly one v1 implementation (`LoggingNotificationSender`) — this is what makes FR-006's "drop-in replacement" claim true structurally, not just by convention.
- No new field, column, or status is added to `NotificationEvent` — this feature is 100% read-only against 036's existing entity, plus the one additive `publishEvent(...)` call in 036's service (FR-007, spec Assumptions).

**Scale/Scope**: Single feature — 1 new Spring event record, 1 new interface, 1 new stub implementation, 1 new listener, 1 one-line addition to 036's existing service, 0 new endpoints, 0 migrations.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Test-First Development | PASS | Tests for every eligible/ineligible channel combination, the after-commit-only firing guarantee, and the no-network/no-config claims (by construction) written before implementation. |
| II. Simplicity & YAGNI | PASS | Exactly the mechanism the source material asks for — no provider selection, no retry/receipt machinery, no delivery-status field (spec Explicitly Out of Scope). Reuses the exact event-publish pattern already established in this codebase (003/008) rather than inventing a new cross-module communication style. |
| III. Modular, Library-First Architecture | PASS | Cross-module communication (036 → this feature) goes through an explicit, event-driven interface (`NotificationEventPublishedEvent`), not a direct reach-through call — exactly what Principle III requires ("event-driven interfaces, not direct reach-through into another module's internals"). |
| IV. Data Privacy & Integrity by Design | PASS | No patient-identifying data is persisted anywhere by this feature (a log line is not new storage); AFTER_COMMIT firing itself is a data-integrity guarantee (never reports a send for un-persisted data). |

No violations — Complexity Tracking is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/012-notification-delivery-stub/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── notification-sender.md
└── tasks.md
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/cms/notification/
│   ├── NotificationEventPublishedEvent.java   # new — plain event record (mirrors ClinicDeVerifiedEvent)
│   ├── NotificationSender.java                # new — the pluggable send contract
│   ├── LoggingNotificationSender.java         # new — v1's only implementation
│   ├── NotificationDeliveryListener.java      # new — @TransactionalEventListener(AFTER_COMMIT)
│   └── NotificationEventService.java          # extended: +ApplicationEventPublisher, +one publishEvent(...) call
└── src/test/java/com/cms/notification/integration/
    ├── AbstractNotificationIntegrationTest.java   # extended: exposes a recording NotificationSender test double
    ├── NotificationDeliverySendsOnlyEligibleChannelsTest.java
    └── NotificationDeliveryOnlyFiresAfterCommitTest.java
```

**Structure Decision**: All new classes live directly in the existing `com.cms.notification` package (not a new sub-module) — this feature is tightly coupled to, and directly extends, 036's own module, matching the precedent of 006/008 adding classes to 005/007's existing packages rather than spinning up a new one for a follow-on feature in the same bounded context. No `frontend/` changes.

## Post-Design Constitution Re-Check

Re-evaluated after Phase 1 (data model + contracts + quickstart, below): all four principles still PASS. The event-driven listener design (data-model.md) confirms Principle III's explicit cross-module-communication requirement holds in the detailed design.

## Complexity Tracking

*No violations — table intentionally left empty.*
