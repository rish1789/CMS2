# Quickstart: Notification Delivery Stub (Log-Only Send)

See [data-model.md](./data-model.md) and [contracts/notification-sender.md](./contracts/notification-sender.md). No HTTP endpoint or UI — exercised via 036's existing `NotificationEventService.publish()`.

## Prerequisites

- Backend running with PostgreSQL and Flyway migrations applied (through 036's `V6` — this feature adds none of its own).
- A Patient Account (039) with known `email`, `mobile`, `pushOptIn`, `smsOptIn`.

## Scenario 1 — Eligible channels each produce one send

1. Create a Patient Account with `pushOptIn=true`, `smsOptIn=true`, a mobile number.
2. Call `NotificationEventService.publish(id, "test.event", "hello", null)`.
3. **Expect**: `NotificationSender.send("push", <email>, ...)` and `NotificationSender.send("sms", <mobile>, ...)` were each called exactly once.

## Scenario 2 — An ineligible channel is never sent

1. Create a Patient Account with `pushOptIn=true`, `smsOptIn=false`.
2. Call `publish`.
3. **Expect**: `send("push", ...)` called once; `send("sms", ...)` never called.

## Scenario 3 — No config needed at startup

1. Start the application with no notification-provider environment variables or config set (none exist in `application.yml` for this feature at all).
2. **Expect**: starts successfully, no warning or error related to notification delivery.

## Scenario 4 — A rolled-back publish never sends

1. Wrap a call to `publish(...)` inside an outer operation that then throws, forcing a rollback (e.g. a test `@Transactional` wrapper that's rolled back).
2. **Expect**: `NotificationSender.send(...)` is never called for that event.
