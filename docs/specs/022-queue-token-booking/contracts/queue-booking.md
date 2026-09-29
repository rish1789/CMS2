# Contract: Queue/Token Booking

## `POST /api/v1/clinics/{clinicId}/sessions/{sessionId}/queue-bookings` (staff)

On `com.cms.identity.account.SecurityConfig`'s existing chain — requires a valid staff JWT
(`Bearer`); the finer-grained "active Operations or ClinicAdmin at this clinic" check happens
in the service layer, same split as every other matcher on that chain.

**Request**:

```json
{ "patientId": "uuid, optional", "patientName": "string, optional", "patientPhone": "string, optional", "appointmentTypeId": "uuid, required" }
```

`patientId` XOR (`patientName` [+ optional `patientPhone`]) — identical semantics to 016's
`BookSlotRequest`.

## `POST /api/v1/patients/clinics/{clinicId}/sessions/{sessionId}/queue-bookings` (patient)

On `com.cms.patient.account.SecurityConfig`'s existing chain — requires a valid Patient
Account JWT.

**Request**:

```json
{ "patientName": "string, required", "appointmentTypeId": "uuid, required" }
```

## Response `201` (both endpoints) — `QueueBookingResponse`, `BookingResponse`'s fields plus `tokenNumber`

```json
{
  "id": "uuid",
  "slotId": "uuid",
  "tokenNumber": 7,
  "patientId": "uuid",
  "appointmentTypeId": "uuid",
  "lockedFee": "300.00",
  "paymentStatus": "PENDING",
  "createdAt": "2026-09-03T10:00:00Z"
}
```

No delay-in-minutes field exists anywhere in this response (spec FR-008) — there is nothing
to strip; `Booking` never had one. `tokenNumber` is what fulfills the user story's "receive a
token number reflecting my place in the queue" — a queue-position *figure* (024, unbuilt) is a
separate, derived concept this feature does not compute.

## Errors (both endpoints; reuse `com.cms.booking.BookingExceptionHandler`)

| Status | Code | When |
|---|---|---|
| 401 | `UNAUTHORIZED` | Missing/invalid token |
| 403 | `FORBIDDEN` | (staff only) Not an active Operations/ClinicAdmin at this clinic |
| 404 | `SESSION_NOT_FOUND` | Session ID doesn't exist, or doesn't belong to `clinicId` |
| 409 | `NOT_A_QUEUE_SESSION` | Session exists but its mode is not `QUEUE` |
| 404 | `APPOINTMENT_TYPE_NOT_FOUND` | Appointment type doesn't exist / doesn't belong to the Session's doctor |
| 409 | `NO_FEE_CONFIGURED` | No fee resolvable for the doctor/appointment-type pairing |
| 404 | `PATIENT_NOT_FOUND` | (staff only) `patientId` given but not found at this clinic |
| 400 | `INVALID_MOBILE_NUMBER` | (staff only) `patientPhone` given but not a valid Indian mobile number |
| 503 | `TOKEN_ISSUANCE_FAILED` | Token issuance exhausted its retry budget under extreme contention (research.md) |

Note: there is no `SLOT_ALREADY_BOOKED` case for either endpoint — every queue booking mints
a brand-new Slot, so the Fixed-Time "this Slot was just taken" race cannot occur here
(research.md).
