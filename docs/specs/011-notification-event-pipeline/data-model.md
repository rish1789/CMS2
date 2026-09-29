# Data Model: Notification Event Pipeline & Opt-In/Out

## `PatientAccount` (extends 039's existing entity — additive migration)

| Field | Type | Change in this feature |
|---|---|---|
| `smsOptIn` | boolean, default `true` | **New** column `sms_opt_in` + getter `isSmsOptIn()` + setter `setSmsOptIn(boolean)` |
| `pushOptIn` | boolean, default `true` | **New** column `push_opt_in` + getter `isPushOptIn()` + setter `setPushOptIn(boolean)` |
| `notificationOptIn` | boolean | Unchanged — untouched by this feature (research.md) |

## `NotificationEvent` (new entity)

| Field | Type | Notes |
|---|---|---|
| `id` | UUID, generated | |
| `patientAccount` | `PatientAccount`, `@ManyToOne`, required | Who the event is for |
| `eventType` | String, required | Opaque, caller-supplied (research.md) |
| `payload` | String, nullable | Opaque event detail — this feature never parses or interprets it |
| `pushEligible` | boolean | Snapshot of `patientAccount.isPushOptIn()` at publish time |
| `smsEligible` | boolean | Snapshot of (`patientAccount.isSmsOptIn()` AND `patientAccount.getMobile() != null`) at publish time — FR-004 |
| `expiresAt` | `Instant`, nullable | `null` means never time-boxed (FR-005) |
| `status` | `NotificationEventStatus` enum (`PENDING`, `ACTIONED`, `EXPIRED`) | Starts `PENDING`; terminal once `ACTIONED` or `EXPIRED` |
| `createdAt` | `Instant`, defaulted `now()` | |

No relationship back from `PatientAccount` to `NotificationEvent` — the FK is one-directional (`NotificationEvent.patientAccount`), matching this codebase's existing pattern (e.g. `Patient.patientAccount` in 009) of not adding a bidirectional collection nothing yet needs.

## Service flow (`NotificationEventService`, all methods `@Transactional`)

### `publish(UUID patientAccountId, String eventType, String payload, Instant expiresAt) -> NotificationEvent`

1. Load `PatientAccount` by id, or throw `PatientAccountNotFoundException`.
2. Compute `pushEligible = patientAccount.isPushOptIn()`.
3. Compute `smsEligible = patientAccount.isSmsOptIn() && patientAccount.getMobile() != null`.
4. Build and save a new `NotificationEvent` (`status = PENDING`, `expiresAt` as given — may be `null`).
5. Return it. No call, in this method or anywhere reachable from it, touches any delivery/send code (there is none in this feature) — this is what makes FR-001/SC-002 true structurally, not by convention.

### `markActioned(UUID eventId) -> NotificationEvent`

1. Load `NotificationEvent` by id, or throw `NotificationEventNotFoundException`.
2. If `status != PENDING` (i.e. already `EXPIRED` — `ACTIONED` is itself terminal so a repeat call also hits this branch, harmlessly), throw `NotificationEventAlreadyExpiredException`.
3. Set `status = ACTIONED`, save, return it.

### `expireDue() -> int`

1. Bulk-update: every `NotificationEvent` where `status = PENDING` and `expiresAt IS NOT NULL` and `expiresAt < now()` transitions to `status = EXPIRED`.
2. Return the count of rows changed. Re-running immediately after matches zero rows (idempotent, FR-007) — a `PENDING` row with no `expiresAt` never matches this predicate at all (FR-005/SC-004), and an already-`ACTIONED`/`EXPIRED` row never matches either.

### `get(UUID eventId) -> NotificationEvent`

Plain lookup, or `NotificationEventNotFoundException` — backs FR-008's read-back requirement.

## Request/Response contract

See `contracts/notification-event-service.md` — this feature has no HTTP endpoint (spec Assumptions; research.md), so the "contract" is this service interface's method signatures and behavior, documented there in the Java-interface-shaped form Constitution Principle III explicitly permits as an alternative to a REST endpoint.
