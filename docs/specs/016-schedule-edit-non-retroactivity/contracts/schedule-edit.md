# Contract: Schedule Edit Non-Retroactivity

Extends `specs/013-recurring-schedule-definition/contracts/schedule.md` — same auth (staff bearer token, `/api/v1/clinics/**` chain), same request/response shape as `POST`.

## `PATCH /api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/schedules/{scheduleId}`

### Request

Identical shape to `POST`'s request (full-payload replacement — research.md):

```json
{
  "daysOfWeek": ["MONDAY", "WEDNESDAY", "FRIDAY"],
  "startTime": "10:00",
  "endTime": "14:00",
  "mode": "FIXED_TIME",
  "slotIntervalMinutes": 20
}
```

### Success Response — `200 OK`

Same shape as `POST`'s `201` response, reflecting the now-updated Schedule.

### Error Responses

| Status | Condition | Body `error` |
|---|---|---|
| `401 Unauthorized` | Missing/invalid staff bearer token | — |
| `403 Forbidden` | Caller is neither an active ClinicAdmin at `clinicId` nor the named doctor themselves | `FORBIDDEN` |
| `404 Not Found` | No `Clinic`/`DoctorProfile` with the path ids, or no `Schedule` with `scheduleId` belonging to that clinic+doctor | `CLINIC_NOT_FOUND` / `DOCTOR_PROFILE_NOT_FOUND` / `SCHEDULE_NOT_FOUND` |
| `400 Bad Request` | The same field-shape rules `POST` enforces | `INVALID_SCHEDULE` |
| `409 Conflict` | The edited configuration would overlap another of the doctor's *other* Schedules | `SCHEDULE_OVERLAP` |

## Contract Invariants (traced to spec)

- Every Session generated from this Schedule before the edit is unchanged in every field after it, regardless of the edit's outcome (FR-007, SC-001).
- A rejected edit (any reason) leaves the Schedule's stored configuration byte-for-byte unchanged (FR-005, SC-002).
- An edit that only changes the Schedule's own fields, with no new overlap against another Schedule, is never rejected for "overlapping itself" (FR-004, SC-003).
- A successful edit's only database write is to the `schedule`/`schedule_day` tables — zero writes to `session` (FR-006, SC-005).
