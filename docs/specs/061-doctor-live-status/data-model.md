# Data Model: Doctor Live Schedule Status

Per spec Assumptions A1/A2: **no new database table, column, or migration**. Every value below is either read directly from an existing entity or computed on demand from existing entities. This document exists to fix the shape of the computed values precisely, not to describe schema changes.

## Existing entities read (unchanged)

### `Session` (`com.cms.scheduling.domain.Session`)

Fields this feature reads: `id`, `sessionDate` (`LocalDate`), `startTime`/`endTime` (`LocalTime`, session-level bounds), `slotIntervalMinutes` (`Integer`), `breakStartTime`/`breakEndTime` (`LocalTime`, nullable pair), `mode` (`ScheduleMode`), `doctorProfile`, `clinic`. No field is written by this feature.

### `Slot` (`com.cms.scheduling.domain.Slot`)

Fields this feature reads: `id`, `session`, `startTime` (`LocalTime`, nullable — null only for Queue-mode slots, which this feature never touches), `status` (`SlotStatus`). No field is written by this feature.

`SlotStatus` values relevant here (existing enum, unchanged): `OPEN`, `BOOKED`, `APPEARED`, `NO_SHOW`, `COMPLETED`.

## Computed value: `SlotProgressionPointer` *(internal calculation concept, not a wire type)*

A reference to one participating `Slot` within a session's ordered sequence, used internally by `SessionLiveStatusService` to derive the two pointers (BR-006/BR-007). Not exposed on any API response directly — callers only ever see the derived ordinal + status + minutes figure below.

- `slotId: UUID`
- `scheduledStartTime: LocalTime`
- `ordinal: int` — 1-based position among *participating* slots only (BR-005), in scheduled order. This is what "Patient 3" means everywhere in this feature.

## Computed value: `SessionLiveStatus` *(staff/doctor read — backs `SessionLiveStatusResponse`)*

| Field | Type | Meaning |
|---|---|---|
| `sessionId` | UUID | Echoes the requested session. |
| `applicable` | boolean | `false` for a Queue-mode session (mirrors `SessionDelayResponse`'s existing contract) — every other field is `null`/absent when `false`. |
| `status` | enum string: `NOT_STARTED` \| `ON_TIME` \| `RUNNING_EARLY` \| `DELAYED` \| `COMPLETED` | BR-001. Raw enum value — this is the staff/doctor surface, which may show a code-like label; the *patient* surface (below) never does (FR-004/FR-011 favor plain language there). |
| `currentPatientOrdinal` | Integer, nullable | The actual pointer's ordinal (BR-006). Null when `COMPLETED` (nothing left to point at) or `NOT_STARTED` (nothing started yet). |
| `expectedPatientOrdinal` | Integer, nullable | The expected pointer's ordinal (BR-007). Null when `NOT_STARTED`. |
| `deviationMinutes` | Integer, nullable | Unsigned minutes figure (BR-008); pair with `status` to know the direction (`DELAYED` → late, `RUNNING_EARLY` → early). Null for `ON_TIME`/`NOT_STARTED`/`COMPLETED`. |
| `firstSlotTime` | LocalTime | The session's own earliest slot's scheduled start time (BR-014) — present regardless of status. |
| `operationalDay` | LocalDate | `OperationalDayService.operationalDateOf(now)` at the moment of the read (BR-011). |

## Computed value: `PatientSessionLiveStatus` *(patient read — backs `PatientSessionLiveStatusResponse`)*

Deliberately a narrower projection of the same underlying calculation — never the full `SessionLiveStatus` shape, per FR-004/FR-011 (no other patient's data, no raw status codes).

| Field | Type | Meaning |
|---|---|---|
| `bookingId` | UUID | Echoes the requested booking. |
| `applicable` | boolean | `false` for a Queue-mode booking. |
| `doctorName` | String | The treating doctor's display name (already-public information on this booking, same as shown elsewhere on the booking detail page). |
| `currentPatientOrdinal` | Integer, nullable | Same meaning as the staff view's `currentPatientOrdinal` — the *only* other-patient-adjacent figure exposed, and it is a bare number, never a name (FR-004). |
| `statusText` | String | Human-readable only (BR-001) — e.g. `"On time"`, `"12 min delayed"`, `"Running 8 min early"`, `"Not started yet"`, `"Session complete"`. No enum code is ever present in the patient-facing payload. |
| `estimatedWaitMinutes` | Integer, nullable | FR-010's derived estimate for the *calling patient's own* slot. Null once that patient's own slot is resolved (already seen) or if their booking's slot doesn't yet have a computable expected time. |

## State model

There is no persisted state machine here — `status` is recomputed from `Slot.status` values on every read (BR-004). The *existing* `SlotStatus` transitions (owned by `SlotAppearedService`/`SlotCompletionService`/`SlotAutoCompletionService`/`NoShowDetectionService`/`BookingCancellationService`, all unchanged by this feature) are what indirectly drive `SessionLiveStatus.status` moving between its five values on each subsequent poll:

```text
SlotStatus changes (existing, unrelated services)
        │
        ▼
SessionLiveStatusService recomputes BR-006/BR-007 pointers fresh
        │
        ▼
SessionLiveStatus.status derived (BR-008) — NOT_STARTED / ON_TIME / RUNNING_EARLY / DELAYED / COMPLETED
```

No new entity, no new table, no new column — confirmed final per spec Assumptions A1/A2.
