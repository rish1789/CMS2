# Contract: Staff-Assisted Fixed-Time Booking

Requires a valid staff bearer token (`/api/v1/clinics/**` chain). A missing/invalid token returns `401`.

## `POST /api/v1/clinics/{clinicId}/slots/{slotId}/book`

### Request

Existing patient:

```json
{ "patientId": "uuid", "appointmentTypeId": "uuid" }
```

New walk-in patient:

```json
{ "patientName": "string", "patientPhone": "string, optional", "appointmentTypeId": "uuid" }
```

### Success Response — `201 Created`

```json
{
  "id": "uuid",
  "slotId": "uuid",
  "patientId": "uuid",
  "appointmentTypeId": "uuid",
  "lockedFee": 500.00,
  "paymentStatus": "PENDING",
  "createdAt": "2026-09-03T10:00:00Z"
}
```

### Error Responses

| Status | Condition | Body `error` |
|---|---|---|
| `401 Unauthorized` | Missing/invalid staff bearer token | — |
| `403 Forbidden` | Caller is neither an active Operations nor ClinicAdmin at `clinicId` | `FORBIDDEN` |
| `404 Not Found` | No `Slot` with `slotId` at `clinicId`, no `Patient` with `patientId` at `clinicId`, or no `AppointmentType` with `appointmentTypeId` for the Slot's doctor | `SLOT_NOT_FOUND` / `PATIENT_NOT_FOUND` / `APPOINTMENT_TYPE_NOT_FOUND` |
| `409 Conflict` | The Slot is not `OPEN` (already booked, incl. a lost concurrent race) | `SLOT_ALREADY_BOOKED` |
| `409 Conflict` | No fee can be resolved (override and default fee both absent) | `NO_FEE_CONFIGURED` |
| `400 Bad Request` | `patientPhone` doesn't match the Indian numbering plan | `INVALID_MOBILE_NUMBER` |

## Contract Invariants (traced to spec)

- A successful booking's `lockedFee` always equals what `FeeResolutionService.resolve` would return for that doctor/appointment-type at that moment (SC-001).
- A `409 NO_FEE_CONFIGURED` response is always accompanied by zero new rows of any kind — no Patient, no Slot mutation, no Booking (SC-002).
- No Slot ever has more than one `Booking`, including under concurrent requests (SC-003).
- A `403` is returned for every caller outside the two authorized roles, regardless of any other field's validity (SC-004).
- A `400 INVALID_MOBILE_NUMBER` response is always accompanied by zero new Patient or Booking rows (SC-005).
