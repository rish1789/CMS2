# Contract: Recurring Schedule Definition

Both endpoints require a valid staff bearer token (`Authorization: Bearer <token>`, issued by `POST /api/v1/staff/login`, 003/004) and live under the existing `/api/v1/clinics/**` security chain (`@Order(1)`). A missing/invalid token returns `401 Unauthorized`.

## `POST /api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/schedules`

### Request

```json
{
  "daysOfWeek": ["MONDAY", "WEDNESDAY", "FRIDAY"],
  "startTime": "09:00",
  "endTime": "13:00",
  "mode": "FIXED_TIME",
  "slotIntervalMinutes": 15
}
```

`slotIntervalMinutes` is omitted (or `null`) for `"mode": "QUEUE"`.

### Success Response — `201 Created`

```json
{
  "id": "uuid",
  "clinicId": "uuid",
  "doctorProfileId": "uuid",
  "daysOfWeek": ["MONDAY", "WEDNESDAY", "FRIDAY"],
  "startTime": "09:00",
  "endTime": "13:00",
  "mode": "FIXED_TIME",
  "slotIntervalMinutes": 15
}
```

### Error Responses

| Status | Condition | Body `error` |
|---|---|---|
| `401 Unauthorized` | Missing/invalid staff bearer token | — |
| `403 Forbidden` | Caller is neither an active ClinicAdmin at `clinicId` nor the named doctor themselves | `FORBIDDEN` |
| `404 Not Found` | No `Clinic` with `clinicId`, or no `DoctorProfile` with `doctorProfileId` | `CLINIC_NOT_FOUND` / `DOCTOR_PROFILE_NOT_FOUND` |
| `409 Conflict` | The named doctor has no *active* Role Assignment at `clinicId` | `DOCTOR_NOT_STAFFED_AT_CLINIC` |
| `400 Bad Request` | `daysOfWeek` empty, `startTime` not before `endTime`, or a `mode`/`slotIntervalMinutes` mismatch | `INVALID_SCHEDULE` |

## `GET /api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/schedules`

Same auth/authorization as `POST` (FR-010). Returns `200 OK` with a JSON array of the same shape as `POST`'s success response (empty array if none exist).

## Contract Invariants (traced to spec)

- Every persisted `Schedule` satisfies all of FR-005–FR-009 — no request violating any one of them ever results in a row (SC-002).
- A request from an actor who is neither the clinic's ClinicAdmin nor the named doctor is always `403`, regardless of any other field's validity (SC-003).
- Neither endpoint ever creates, modifies, or references a Session/Slot row (FR-011, SC-004).
