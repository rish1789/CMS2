# Contract: Walk-In / Priority Insertion

Requires a valid staff bearer token (`/api/v1/clinics/**` chain). A missing/invalid token returns `401`.

## `POST /api/v1/clinics/{clinicId}/sessions/{sessionId}/walk-in`

Staff never choose the target Slot directly — the system selects it via the strict priority order
(FR-001): (1) an OPEN buffer Slot, (2) a Slot marked `NO_SHOW`, (3) any other OPEN regular Slot
(requires `overrideReason`).

### Request

Existing patient:

```json
{ "patientId": "uuid", "appointmentTypeId": "uuid", "overrideReason": "string, optional" }
```

New walk-in patient:

```json
{
  "patientName": "string",
  "patientPhone": "string, optional",
  "appointmentTypeId": "uuid",
  "overrideReason": "string, optional"
}
```

`overrideReason` is only actually required when the system determines a priority-(3) insertion is
the only available path — the client cannot know this in advance (that's the point of the
priority search), so it is always an optional request field, validated as required only at that
point in server-side processing (FR-003).

### Success Response — `201 Created`

Reuses 020's `BookingResponse` shape exactly, since a walk-in insertion produces the same kind of
result (a confirmed Booking with a locked fee):

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
| `404 Not Found` | No `Session` with `sessionId` at `clinicId`, no `Patient` with `patientId` at `clinicId`, or no `AppointmentType` with `appointmentTypeId` for the Session's doctor | `SESSION_NOT_FOUND` / `PATIENT_NOT_FOUND` / `APPOINTMENT_TYPE_NOT_FOUND` |
| `409 Conflict` | No Slot qualifies at any priority tier (FR-009) | `NO_SLOT_AVAILABLE` |
| `400 Bad Request` | Priority-(3) is the only available tier and `overrideReason` is blank/absent (FR-003) | `OVERRIDE_REASON_REQUIRED` |
| `409 Conflict` | The system-selected Slot is no longer available (lost a concurrent race — FR-010) | `SLOT_ALREADY_BOOKED` |
| `409 Conflict` | No fee can be resolved (override and default fee both absent) | `NO_FEE_CONFIGURED` |
| `400 Bad Request` | `patientPhone` doesn't match the Indian numbering plan | `INVALID_MOBILE_NUMBER` |

## Contract Invariants (traced to spec)

- A successful insertion always uses the highest-priority eligible Slot available at the moment of
  insertion — never a lower-priority one while a higher-priority one exists (SC-001).
- Every `400 OVERRIDE_REASON_REQUIRED` response is accompanied by zero new rows of any kind — no
  Patient, no Slot mutation, no Booking, and (for a would-be tier-2 insertion that instead lands on
  tier 3 because a different Session's no-show slot doesn't apply here) no deletion of any existing
  Booking (SC-002).
- Every `409 NO_FEE_CONFIGURED` response is accompanied by zero new rows and zero deletions — in
  particular, a priority-(2) candidate's original no-show `Booking` is never deleted unless the new
  walk-in `Booking` is actually created successfully in the same call (SC-003, FR-001a).
- Every successful priority-(3) insertion's `Booking.overrideReason` is non-null and retrievable
  afterward (SC-004).
- No Slot ever ends up with more than one `Booking`, including under concurrent requests targeting
  the same Session (SC-005).
