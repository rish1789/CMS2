# Implementation Plan: Notification Event Pipeline & Opt-In/Out

**Branch**: `011-notification-event-pipeline` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/011-notification-event-pipeline/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

A new `com.cms.notification` module providing a service-only contract (no HTTP endpoint — its callers, 016/017/018/029, don't exist yet, per spec Assumptions): `NotificationEventService.publish(...)` creates a `NotificationEvent` row recording per-channel (push/SMS) eligibility computed from the patient's own opt-in flags at that instant, and returns immediately with no delivery side effect. `markActioned(...)` and `expireDue()` implement the generic notify-once/auto-expire lifecycle for events published with an optional expiration window. `PatientAccount` (039) gains two new, additive fields (`smsOptIn`, `pushOptIn`) via one new migration; its existing `notificationOptIn` field is untouched. No new migration touches any other existing table.

## Technical Context

**Language/Version**: Java 21 (backend-only — no frontend surface; per spec Assumptions, no UI or cross-service caller exists yet that would need one, mirroring 009's precedent).

**Primary Dependencies**: Spring Boot 3.x (Data JPA, Transaction) — reuses `com.cms.patient.account.PatientAccount`/`PatientAccountRepository` (039) directly. No new external dependency.

**Storage**: PostgreSQL — one new migration (`V6`): adds `sms_opt_in`/`push_opt_in` columns to the existing `patient_account` table, and creates the new `notification_event` table.

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers (backend only).

**Target Platform**: Linux container (Docker).

**Project Type**: Backend service module (no web/API layer in this feature).

**Performance Goals**: Same order of magnitude as prior features (2s p95) — `publish` is a single-row insert plus a read of the caller's own already-loaded `PatientAccount`; `expireDue` is a single bulk update over rows matching an indexed predicate.

**Constraints**:
- `publish` MUST NOT call, block on, or depend on any delivery/send code path — this feature contains none (FR-001, SC-002).
- Channel-eligibility MUST be computed and recorded once, at publish time — never recomputed later from the (possibly since-changed) live preference (FR-002; spec explicitly rejects recomputing later, since eligibility is what happened at that moment, not what's true now).
- `expireDue` MUST be safely re-runnable with no side effect on an already-expired or already-actioned event (FR-007) — implemented as a bulk conditional update (`WHERE status = 'PENDING' AND expires_at < :now`), which is naturally idempotent since a second run matches zero rows.
- `markActioned` on an already-expired event MUST be rejected, not silently allowed to "win" a race with a since-run expiry sweep (spec Edge Cases) — implemented as a status-guarded update; a lost race throws a domain exception, mirroring this codebase's existing not-found/conflict exception pattern (e.g. `DuplicateLicenseNumberException`, `PatientAccountNotFoundException`).

**Scale/Scope**: Single feature — 1 new entity (`NotificationEvent`), 1 migration (2 DDL changes bundled — additive columns + new table), 2 new repositories' worth of methods (one on a new `NotificationEventRepository`, plus 2 new fields + getters/setters on the existing `PatientAccount`), 1 new service, 0 new endpoints.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Test-First Development | PASS | Tests for every FR (channel-eligibility computation incl. no-phone-number/SMS interaction, publish's non-blocking contract, expiry sweep idempotency and actioned-event protection, actioned-after-expired rejection) written before implementation. |
| II. Simplicity & YAGNI | PASS | Deliberately builds only the generic mechanism the backlog's own build-order.md resolution calls for — no fabricated booking/waitlist/follow-up call-sites, no HTTP endpoint with no caller, no event-type enum invented ahead of the features that will define real event types (spec Assumptions). |
| III. Modular, Library-First Architecture | PASS | New `com.cms.notification` module, testable in isolation; reads `PatientAccount` (patient module) as a read-only cross-module reference, matching the established pattern. Service-interface contract (Constitution's explicit alternative to a REST endpoint), since no caller exists yet. |
| IV. Data Privacy & Integrity by Design | PASS | Respects patient opt-out preference as a hard gate on channel eligibility (the feature's core privacy behavior); expiry sweep and actioned-guard are both implemented as data-layer-safe conditional updates, not application-level check-then-act races. |

No violations — Complexity Tracking is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/011-notification-event-pipeline/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── notification-event-service.md
└── tasks.md
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/cms/notification/
│   ├── NotificationEvent.java                       # new entity
│   ├── NotificationEventStatus.java                  # new enum (PENDING, ACTIONED, EXPIRED)
│   ├── NotificationEventRepository.java              # new
│   ├── NotificationEventService.java                 # new — the feature's sole public contract
│   ├── NotificationEventNotFoundException.java        # new
│   ├── NotificationEventAlreadyExpiredException.java   # new
│   └── PatientAccountNotFoundException.java           # new (local to this module, not reused from patient.record)
├── src/main/java/com/cms/patient/account/
│   └── PatientAccount.java                           # extended: +smsOptIn, +pushOptIn fields/getters/setters
├── src/main/resources/db/migration/
│   └── V6__notification_event_pipeline.sql            # new — patient_account columns + notification_event table
└── src/test/java/com/cms/notification/
    └── integration/
        ├── AbstractNotificationIntegrationTest.java
        ├── PublishChannelEligibilityTest.java
        ├── PublishNoPhoneNumberSmsIneligibleTest.java
        ├── PublishAccountAndEventNotFoundTest.java
        ├── ExpireDueSweepTest.java
        ├── ExpireDueIdempotentTest.java
        ├── ExpireDueLeavesNonTimeBoxedEventsAloneTest.java
        ├── MarkActionedProtectsFromExpiryTest.java
        └── MarkActionedOnExpiredEventRejectedTest.java
```

**Structure Decision**: A new top-level `com.cms.notification` backend module, sibling to `com.cms.identity`, `com.cms.patient`, and `com.cms.discovery` — genuinely new territory (no existing module owns "notification" as a concern), reading `PatientAccount` as a read-only cross-module reference the same way `com.cms.discovery` reads `Clinic`/`DoctorProfile`. `PatientAccount` itself is extended in place (additive only) rather than duplicated or wrapped, since the two new fields are intrinsically patient-identity data, not notification-pipeline data. No `frontend/` changes — no UI consumer exists yet (spec Assumptions).

## Post-Design Constitution Re-Check

Re-evaluated after Phase 1 (data model + contracts + quickstart, below): all four principles still PASS. The status-guarded, bulk-update expiry/actioned design (data-model.md) confirms Principle IV's data-layer-safety requirement holds in the detailed design, not just at the plan-summary level.

## Complexity Tracking

*No violations — table intentionally left empty.*
