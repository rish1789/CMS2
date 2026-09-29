# Phase 1 Data Model: Day Sheet Hardening

## No new entities

This feature introduces zero new tables/entities. It extends two existing API response shapes and adds one index to the existing `session` table. `Session`, `Slot`, `Booking`, `DoctorProfile`, and `Account` are all reused exactly as they already exist.

## Extended response: session list (`SessionListResponse` / `SessionSummaryResponse`)

| Field | Type | Status | Source |
|---|---|---|---|
| `sessions` | array of `SessionSummaryResponse` | existing, now one page not the full window | `Session`, paginated |
| `sessions[].sessionId` | uuid | existing | `Session.id` |
| `sessions[].doctorProfileId` | uuid | existing | `Session.doctorProfile.id` |
| `sessions[].doctorName` | string | existing | `Session.doctorProfile.account.name` |
| `sessions[].sessionDate` | date | existing | `Session.sessionDate` |
| `sessions[].mode` | `FIXED_TIME`\|`QUEUE` | existing | `Session.mode` |
| `sessions[].bookedSlotCount` | integer | **new** (FR-011) | bulk count over `Slot`/`Booking` for this session, active bookings only |
| `sessions[].totalSlotCount` | integer | **new** (FR-011) | bulk count of `Slot` rows for this session |
| `doctors` | array of `{ doctorProfileId, name }` | **new** (FR-004) | distinct doctors with ≥1 session in the current window, independent of the current page/filter |
| `page` | integer | **new** (FR-005) | requested page, 0-based |
| `pageSize` | integer | **new** (FR-005) | requested page size |
| `totalCount` | integer | **new** (FR-005) | total sessions matching the window/filter, across all pages |

`totalSlotCount` of `0` is a valid, meaningful value (no slots generated yet for that session) and MUST be rendered distinctly from a fraction (see spec Edge Cases) — not as "0 of 0".

## Extended response: session day sheet (`SessionDaySheetResponse`)

| Field | Type | Status | Source |
|---|---|---|---|
| `sessionId` | uuid | existing | `Session.id` |
| `doctorProfileId` | uuid | existing | `Session.doctorProfile.id` |
| `doctorName` | string | **new** (FR-010) | `Session.doctorProfile.account.name` |
| `sessionDate` | date | **new** (FR-010) | `Session.sessionDate` |
| `mode` | `FIXED_TIME`\|`QUEUE` | existing | `Session.mode` |
| `slots` | array of `SlotDetail` | existing, unchanged | `Slot` + active `Booking` |

## New index

| Name | Table | Columns | Purpose |
|---|---|---|---|
| `idx_session_clinic_id_session_date` | `session` | `(clinic_id, session_date)` | Supports the list endpoint's clinic + date-range filter (R8) — no index currently covers this query. |

## Unchanged

- Doctor self-scoping authorization (a caller whose only active role at the clinic is Doctor sees only their own sessions) — same filter, now composed with the new pagination/doctor-filter params rather than replaced by them.
- `SlotDetail`, `BookingDetail` shapes on the day-sheet response — untouched.
- The whole-session-cancellation endpoint's request/response contract — the confirm step (FR-001/002/003) is frontend-only.
