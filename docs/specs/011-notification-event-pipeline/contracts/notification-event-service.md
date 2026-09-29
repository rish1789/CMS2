# Contract: NotificationEventService (service interface, not a REST endpoint)

Per Constitution Principle III's explicit allowance ("a clear contract — service interface or REST endpoint") and this feature's own Assumptions: no HTTP endpoint exists yet, since every eventual caller (016/017/018/029, plus a future ops trigger for `expireDue`) is unbuilt. This is the contract those features will code against once they exist. All methods live in `com.cms.notification.NotificationEventService` and are `@Transactional`.

## `publish(UUID patientAccountId, String eventType, String payload, Instant expiresAt) -> NotificationEvent`

### Parameters

| Name | Required | Notes |
|---|---|---|
| `patientAccountId` | yes | Must reference an existing `PatientAccount` (039) |
| `eventType` | yes | Opaque, caller-supplied identifier (e.g. a future `"booking.confirmed"`) — this service never inspects or branches on its value (research.md) |
| `payload` | no | Opaque event detail; stored as-is, never parsed |
| `expiresAt` | no (`null` allowed) | `null` means the event is never time-boxed and is never touched by `expireDue()` |

### Return

The newly created `NotificationEvent`, `status = PENDING`, with `pushEligible`/`smsEligible` already computed and set.

### Exceptions

| Exception | Condition |
|---|---|
| `PatientAccountNotFoundException` | No `PatientAccount` with `patientAccountId` |

### Guarantee

Returns without invoking, awaiting, or depending on any delivery/send code path — none exists in or is reachable from this feature.

## `markActioned(UUID eventId) -> NotificationEvent`

Marks a still-`PENDING`, time-boxed (or non-time-boxed) event as `ACTIONED`, permanently protecting it from `expireDue()`.

### Exceptions

| Exception | Condition |
|---|---|
| `NotificationEventNotFoundException` | No `NotificationEvent` with `eventId` |
| `NotificationEventAlreadyExpiredException` | The event's `status` is not `PENDING` (already `EXPIRED`, or a repeat call after it's already `ACTIONED`) |

## `expireDue() -> int`

Bulk-transitions every `PENDING` event whose `expiresAt` has lapsed to `EXPIRED`. Returns the number of events transitioned. Safe to call repeatedly (idempotent — a second immediate call returns `0`).

## `get(UUID eventId) -> NotificationEvent`

Plain read-back of an event's current state.

### Exceptions

| Exception | Condition |
|---|---|
| `NotificationEventNotFoundException` | No `NotificationEvent` with `eventId` |

## Contract Invariants (traced to spec)

- `publish`'s recorded `pushEligible`/`smsEligible` always reflects the patient's opt-in state *at the moment of the call*, independent of any later preference change (FR-002, SC-001).
- `smsEligible` is always `false` when the patient has no `mobile` on file, regardless of `smsOptIn` (FR-004).
- An event published with `expiresAt = null` is never transitioned by `expireDue()`, no matter how many times or how much later it's called (FR-005, SC-004).
- An event `markActioned` before its window lapses is never transitioned by any subsequent `expireDue()` call (FR-006, SC-003).
- An event whose window has lapsed and was never actioned is `EXPIRED` after the very next `expireDue()` call, and stays `EXPIRED` after any further calls (FR-007, SC-003).
- `markActioned` on an already-`EXPIRED` event always throws `NotificationEventAlreadyExpiredException` — it never flips back to `ACTIONED` (Edge Cases).
