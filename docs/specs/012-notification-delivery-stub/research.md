# Research: Notification Delivery Stub (Log-Only Send)

## Decision: `@TransactionalEventListener(phase = AFTER_COMMIT)`, mirroring the `ClinicDeVerifiedEvent` precedent

**Rationale**: This codebase already has exactly this cross-feature-time-gap shape: `003-super-admin-clinic-verification`'s `ClinicVerificationService.unverify()` publishes `ClinicDeVerifiedEvent` via a plain `eventPublisher.publishEvent(...)` call with no listener at the time (008 hadn't been built yet); 036 is in the same position for this feature now. The publishing side needs no special annotation — it's an ordinary `ApplicationEventPublisher.publishEvent(...)` call inside `@Transactional publish()`. The *listening* side is what determines timing: `@TransactionalEventListener(phase = AFTER_COMMIT)` defers execution until the publishing transaction has actually committed, which is exactly FR-005/SC-004's requirement (never log a send for data that didn't actually get persisted). A plain `@EventListener` would fire even inside a transaction that later rolls back, which is wrong here.

**Alternatives considered**: A message queue / outbox pattern — rejected as far beyond what a log-only stub needs (Principle II); nothing in the source material asks for durability or retry, and this project has no messaging infrastructure anywhere else. `@Async` — rejected; a log statement has no meaningful latency to hide, and `AFTER_COMMIT`'s synchronous-but-post-commit timing already satisfies every acceptance scenario without adding thread-pool complexity.

## Decision: `NotificationSender` interface + `LoggingNotificationSender` single implementation

**Rationale**: FR-006 requires the contract to be substitutable without touching 036 or this feature's own channel-eligibility-reading logic. A one-method interface (`send(String channel, String recipient, String message)`) captures exactly what a "send step" needs to know, with the `NotificationDeliveryListener` responsible for deciding *which* channels to call it for (reading `NotificationEvent.pushEligible`/`smsEligible`) and *what* recipient/message to pass — the interface itself never needs to know about `NotificationEvent`, `PatientAccount`, or eligibility rules at all, which is what keeps a future real implementation a pure drop-in.

**Alternatives considered**: A single concrete `NotificationSender` class with a feature flag / provider-switch inside it — rejected; that's exactly the "rework the pipeline" outcome FR-006 says to avoid. Passing the whole `NotificationEvent`/`PatientAccount` into the sender — rejected; it would force every future real implementation to depend on this codebase's domain entities instead of the minimal (channel, recipient, message) shape an actual provider SDK would want.

## Decision: recipient identifiers — `mobile` for SMS, `email` for push

**Rationale**: See spec Assumptions — no push-token/device-registration concept exists anywhere in this 39-feature backlog. `email` (a required `PatientAccount` field, per 039) is the only patient identifier that's always available to stand in for a push recipient. SMS always uses `mobile`, which 036's own FR-004 guarantees is non-null whenever `smsEligible` is true.

**Alternatives considered**: Using the raw `patientAccountId` (UUID) as the push "recipient" — rejected; a human-readable identifier (email) is more useful for the log's actual purpose (a developer verifying what would have been sent) than an opaque id, and costs nothing extra to include (already loaded on the `PatientAccount` this event's snapshot data traces back to).

## Decision: `NotificationEventPublishedEvent` carries a full snapshot, not just an id

**Rationale**: Since the listener fires `AFTER_COMMIT`, it *could* safely re-fetch the `NotificationEvent` by id (the data is guaranteed committed). But 036's `NotificationEvent` doesn't itself carry the patient's `email`/`mobile` (those live on `PatientAccount`) — re-fetching would mean the listener also needs a `PatientAccountRepository` dependency and a second query, just to assemble what `publish()` already had in hand at the moment it decided eligibility. Carrying `(notificationEventId, eventType, payload, pushEligible, smsEligible, mobile, email)` directly in the event keeps the listener a pure, single-purpose reader with no extra queries or repository dependencies.

**Alternatives considered**: An id-only event with the listener re-fetching both `NotificationEvent` and `PatientAccount` — rejected as an unnecessary two extra queries and a new repository dependency for a component whose only job is logging, for no benefit (nothing here is stale-data-sensitive, since `publish()` already computed and snapshotted eligibility once).

## Decision: extend 036's `NotificationEventService.publish()` directly, not a new wrapper service

**Rationale**: Per spec Assumptions — this is a small, additive, one-call change (inject `ApplicationEventPublisher`, call `publishEvent(...)` once, after `save()`), and 036's own `publish()` is the single, canonical place a `NotificationEvent` is ever created. Wrapping it in a new "publish-and-notify" service instead would create two ways to publish an event (one that fires the send step, one that doesn't), which is exactly the kind of accidental-inconsistency risk Constitution Principle III's "explicit, event-driven interfaces, not direct reach-through" is meant to prevent.

**Alternatives considered**: A separate `NotifyingPublishService` that wraps `NotificationEventService.publish()` and then fires the event itself — rejected; it doesn't prevent a caller from bypassing it and calling the original `publish()` directly, silently skipping delivery, which defeats FR-001's "MUST invoke... for every channel... eligible."
