# Contract: Multi-Clinic Doctor Schedule Overlap Block

Extends `specs/013-recurring-schedule-definition/contracts/schedule.md`'s `POST /api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/schedules` — no new endpoint, one new possible error response.

## `POST /api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/schedules` (extended)

### New Error Response

| Status | Condition | Body `error` |
|---|---|---|
| `409 Conflict` | The submitted Schedule shares a day-of-week and an overlapping time range with any of the doctor's existing Schedules, at this clinic or any other | `SCHEDULE_OVERLAP` |

Checked only after every other validation (`400 INVALID_SCHEDULE`), not-found (`404`), staffing-gate (`409 DOCTOR_NOT_STAFFED_AT_CLINIC`), and authorization (`403 FORBIDDEN`) checks have already passed (research.md's precedence decision).

## Contract Invariants (traced to spec)

- A submission overlapping any existing Schedule for that doctor — at any clinic — is always `409 SCHEDULE_OVERLAP`, never `201` (FR-001, FR-002, SC-001).
- A submission whose time range only touches (never overlaps) an existing same-day Schedule is always `201`, never `409 SCHEDULE_OVERLAP` (FR-003, SC-002).
- A submission sharing no day with any existing Schedule is always `201` regardless of time range (FR-004, SC-003).
- A doctor's first-ever Schedule submission is never rejected for overlap (SC-004).
