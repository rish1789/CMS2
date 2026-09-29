# Data Model: Notification Delivery Stub (Log-Only Send)

No new table, column, or migration. This feature adds one in-process event type and one small service interface/implementation pair.

## `NotificationEventPublishedEvent` (new — plain event record, mirrors `ClinicDeVerifiedEvent`)

| Field | Type | Source |
|---|---|---|
| `notificationEventId` | UUID | `NotificationEvent.id` |
| `eventType` | String | `NotificationEvent.eventType` |
| `payload` | String (nullable) | `NotificationEvent.payload` |
| `pushEligible` | boolean | `NotificationEvent.pushEligible` |
| `smsEligible` | boolean | `NotificationEvent.smsEligible` |
| `mobile` | String (nullable) | `PatientAccount.mobile` at publish time |
| `email` | String | `PatientAccount.email` at publish time |
| `occurredAt` | Instant | `Instant.now()` at publish time |

Static factory `NotificationEventPublishedEvent.of(NotificationEvent event, PatientAccount patientAccount)`, matching `ClinicDeVerifiedEvent.of(...)`'s existing shape.

## `NotificationSender` (new interface — the pluggable "send" contract, FR-006)

```java
public interface NotificationSender {
    void send(String channel, String recipient, String message);
}
```

## `LoggingNotificationSender` (new — the only v1 implementation)

Logs one `INFO` line per call: channel, recipient, message. No field, no dependency, no configuration — nothing to fail at startup (FR-004, SC-003).

## `NotificationDeliveryListener` (new — `@Component`, `@TransactionalEventListener(phase = AFTER_COMMIT)`)

1. Receives `NotificationEventPublishedEvent` after 036's `publish()` transaction commits (FR-005, SC-004 — never fires for a rolled-back publish).
2. If `pushEligible`, call `notificationSender.send("push", event.email(), message)`.
3. If `smsEligible`, call `notificationSender.send("sms", event.mobile(), message)`.
4. `message` is derived from `eventType`/`payload` (e.g. a simple concatenation) — this feature does not interpret or template the payload; it passes through whatever 036's caller supplied.

Neither eligible-channel call depends on or waits on the other; each is an independent `send` invocation (FR-001, FR-003).

## `NotificationEventService.publish()` (extended — 036, one additive line)

After `notificationEventRepository.save(event)` succeeds, call `eventPublisher.publishEvent(NotificationEventPublishedEvent.of(event, patientAccount))` — the `PatientAccount` is already loaded in this method (needed for eligibility computation), so no extra query is introduced. `publish()`'s own return value and transactional boundary are otherwise completely unchanged.

## Request/Response contract

See `contracts/notification-sender.md` — this feature has no HTTP endpoint (nothing user-facing); the "contract" is `NotificationSender`'s interface, documented there in the Java-interface-shaped form Constitution Principle III explicitly permits.
