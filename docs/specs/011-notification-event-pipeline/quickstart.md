# Quickstart: Notification Event Pipeline & Opt-In/Out

See [data-model.md](./data-model.md) and [contracts/notification-event-service.md](./contracts/notification-event-service.md).

This feature has no HTTP endpoint or UI (spec Assumptions) — every scenario below is exercised directly against `NotificationEventService`, the way 016/017/018/029 will once they exist.

## Prerequisites

- Backend running with PostgreSQL and Flyway migrations applied (through this feature's own `V6`).
- A Patient Account (039) to publish events for.

## Scenario 1 — Channel eligibility reflects opt-in state at publish time

1. Create a Patient Account with `smsOptIn = true`, `pushOptIn = true`, and a mobile number set.
2. Call `publish(id, "test.event", null, null)`. **Expect**: returned event has `pushEligible = true`, `smsEligible = true`.
3. Flip the account's `smsOptIn` to `false`. Call `publish` again. **Expect**: new event has `smsEligible = false`, `pushEligible = true` — independent of each other.
4. Re-fetch the *first* event from Scenario step 2. **Expect**: still `smsEligible = true` — unaffected by the later preference change (the snapshot is immutable).

## Scenario 2 — No phone number makes SMS ineligible regardless of opt-in

1. Create a Patient Account with `smsOptIn = true` and no mobile number.
2. Call `publish`. **Expect**: `smsEligible = false` despite the opt-in flag being `true`.

## Scenario 3 — Time-boxed event expires if never actioned

1. Call `publish(id, "test.event", null, expiresAt = 1 second in the past)` (or publish with a near-future `expiresAt` and wait/advance the clock).
2. Call `expireDue()`. **Expect**: returns `1` (or more, if other due events exist); the event's `status` is now `EXPIRED`.
3. Call `expireDue()` again. **Expect**: returns `0` — no further change.
4. Call `markActioned` on the now-expired event. **Expect**: throws `NotificationEventAlreadyExpiredException`.

## Scenario 4 — Actioning an event before its window lapses protects it

1. Call `publish` with a future `expiresAt`.
2. Call `markActioned` on the returned event's id. **Expect**: `status = ACTIONED`.
3. Advance past the `expiresAt` time (or use a past-dated test fixture) and call `expireDue()`. **Expect**: the event remains `ACTIONED`, not `EXPIRED`.

## Scenario 5 — A non-time-boxed event is never touched by expiry

1. Call `publish(id, "test.event", null, null)` (no `expiresAt`).
2. Call `expireDue()` any number of times. **Expect**: the event's `status` stays `PENDING` throughout.

## Scenario 6 — Publish never blocks on delivery

1. Call `publish`. **Expect**: returns in well under the feature's normal 2s p95 performance goal, and (by code inspection) no delivery/send call exists anywhere in the call path — there is no such code in this feature at all.
