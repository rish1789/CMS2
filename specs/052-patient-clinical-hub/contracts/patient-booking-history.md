# Contract: Patient Booking History

## `GET /api/v1/clinics/{clinicId}/patients/{patientId}/bookings?page=&size=`

Lists the given patient's bookings at this clinic, newest session-date first. Reuses `PatientDetailController`'s own established clinic-ownership check: `patientId` must belong to `clinicId`, or `404`. Same "any active role at this clinic" gate as `PatientDetailController`/`ClinicPatientSearchController` — no new authorization mechanism.

### Success Response — `200 OK`

```json
{
  "bookings": [
    {
      "bookingId": "uuid",
      "sessionDate": "2026-09-10",
      "startTime": "09:30:00",
      "doctorName": "Dr. Mehta",
      "appointmentTypeName": "General Consultation",
      "bookingStatus": "ACTIVE",
      "slotStatus": "COMPLETED"
    }
  ],
  "page": 0,
  "pageSize": 20,
  "totalCount": 7
}
```

### Error Responses

| Status | Condition |
|---|---|
| `401 Unauthorized` | Missing/invalid staff bearer token |
| `403 Forbidden` (`FORBIDDEN`) | Caller has no active `RoleAssignment` at `clinicId` (matches `PatientDetailController`'s existing `NotStaffedAtClinicException` → `FORBIDDEN` mapping) |
| `404 Not Found` (`PATIENT_NOT_FOUND`) | No `Patient` with that id, or it belongs to a different clinic than `clinicId` — identical to `PatientDetailController`'s existing behavior, same status/code, so a stale/cross-clinic id can't be distinguished from a nonexistent one |

## Updated Contract: `GET /api/v1/clinics/{clinicId}/patients/{patientId}` and `GET /api/v1/clinics/{clinicId}/patients/search`

Both now additionally return `anonymizedAt` (ISO-8601 timestamp or `null`) alongside the existing `patientId`/`name`/`phone` fields. No other change to either endpoint's request shape, status codes, or authorization.

## Contract Invariants (traced to spec)

- Every booking returned belongs to the current clinic's own `Patient` record — never another clinic's relationship with the same person (FR-003, SC-002), enforced structurally by scoping the query to a `patientId` already confirmed to belong to `clinicId`.
- `anonymizedAt` always reflects the current, live value — never cached, never a separate code path from the value 033's own anonymize endpoint writes (FR-002, SC-003).
- No endpoint introduced or modified by this feature accepts or returns consultation-note/prescription/external-record content — those stay exclusively on their existing, unmodified per-booking endpoints (FR-004, FR-006).
