# Phase 1 Data Model: Day Sheet Smart Status Flow

No new entity or table. One existing entity's status enum gains one value; no other field changes.

## `Slot` (existing entity, `com.cms.scheduling.domain.Slot`)

### `SlotStatus` (existing enum, extended)

| Value | Meaning | New in this feature? |
|---|---|---|
| `OPEN` | Bookable, no active booking | No |
| `BOOKED` | An active `Booking` exists, patient not yet confirmed present | No |
| `APPEARED` | Patient has arrived and is with the doctor | **Yes** |
| `NO_SHOW` | Automatic sweep found no patient arrival within the grace period | No (behavior extended — see transitions) |
| `COMPLETED` | Visit finished | No (now also reachable automatically, not only manually) |

### State transitions

```
OPEN --(booking created, 020/021)--> BOOKED

BOOKED --(mark Appeared, ClinicAdmin/Operations, manual)--> APPEARED
BOOKED --(no Appeared within grace period, automatic sweep, unchanged timing)--> NO_SHOW
BOOKED --(cancel, existing rule, unchanged)--> OPEN
BOOKED --(mark Completed, ClinicAdmin/Operations only, manual — existing path, unchanged)--> COMPLETED

NO_SHOW --(mark Appeared, ClinicAdmin/Operations, manual — correction)--> APPEARED

APPEARED --(scheduled end time passes, automatic sweep)--> COMPLETED
APPEARED --(mark Completed early, ClinicAdmin/Operations or treating Doctor, manual)--> COMPLETED
APPEARED --(cancel, extended rule — this feature)--> OPEN

COMPLETED --(none — terminal, unchanged)--> (no further transition)
```

Every transition above is either already implemented and unchanged by this feature (`OPEN→BOOKED`, `BOOKED→NO_SHOW`'s timing, `BOOKED→OPEN` via cancel, `BOOKED→COMPLETED` for ClinicAdmin/Operations, `COMPLETED` as terminal) or newly added by it (every edge touching `APPEARED`, and `APPEARED→OPEN` via cancel). **`BOOKED→COMPLETED` is deliberately left in place, unchanged, for ClinicAdmin/Operations** — this feature is additive (FR-008); it does not force every completion through Appeared for the roles that already have direct access today. It is additionally reachable via `APPEARED→COMPLETED`, which is the *only* path available to a treating Doctor (FR-006 scopes their access to "their own Appeared slot" specifically — a Doctor cannot complete a still-`BOOKED` slot). No transition into `OPEN` from `NO_SHOW` or `COMPLETED` is introduced — a No-Show is only correctable forward into `APPEARED` (Decision 3), never directly back to bookable, and a Completed slot has no exit transition at all, matching its existing terminal contract.

### Validation rules

- **Appeared is only reachable from `BOOKED` or `NO_SHOW`** — attempting to mark any other status (`OPEN`, `APPEARED` itself, `COMPLETED`) Appeared is rejected.
- **Automatic completion only ever touches `APPEARED` slots** whose `session.sessionDate` + `slot.endTime` has passed — mirrors the existing No-Show sweep's own `LocalDateTime.of(session.getSessionDate(), slot.getStartTime())` construction, using `endTime` instead of `startTime`.
- **Automatic No-Show detection is unchanged**: still only touches `BOOKED` slots (Appeared already removes a slot from that candidate set structurally, since the existing query filters on `status = BOOKED`).
- **Cancellation is only reachable from `BOOKED` or `APPEARED`** (extended from `BOOKED`-only today) — a `NO_SHOW`, `COMPLETED`, or already-`OPEN` slot cannot be cancelled, unchanged.
- **Fixed-Time only**: every transition above applies only when `slot.session.mode == FIXED_TIME`, mirroring the existing scope of both the manual completion action and the No-Show sweep it extends.

## No new persisted entity for batch selection

The Day Sheet's checkbox selection (single/multiple/all) is a transient, client-side-only set of `slotId`/`bookingId` values assembled in the browser and sent as a plain list to the batch-cancel endpoint in one request — it is never persisted, has no lifecycle of its own, and requires no schema.
