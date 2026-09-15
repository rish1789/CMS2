# Contract: Patient Self-Service Booking

Both endpoints live on `com.cms.patient.account.SecurityConfig`'s existing
`/api/v1/patients/**` security chain and require a valid Patient Account Bearer JWT
(`aud=patient`, per `com.cms.patient.account.JwtService`). A missing/invalid/expired token
returns `401 {"error":"UNAUTHORIZED"}`.

## `GET /api/v1/patients/clinics/{clinicId}/slots`

Lists currently-OPEN Fixed-Time Slots at a clinic.

**Query params**: `doctorId` (optional `UUID`) — filters to one doctor.

**Response `200`**:

```json
[
  {
    "slotId": "uuid",
    "doctorProfileId": "uuid",
    "doctorName": "Dr. Asha Rao",
    "sessionDate": "2026-09-10",
    "startTime": "09:00:00",
    "endTime": "09:15:00",
    "appointmentTypes": [
      { "id": "uuid", "doctorProfileId": "uuid", "name": "General Consultation", "feeOverride": null }
    ]
  }
]
```

`feeOverride` is passed through as-is from `AppointmentType` (may be `null`, meaning the
doctor's default fee applies) — this is metadata about the appointment-type configuration,
not a resolved/locked booking fee (research.md's "list omits fee amounts" decision refers to
not calling `FeeResolutionService`; echoing the type's own configured override, if any, costs
nothing extra and is not the same as computing a resolved fee).

An empty array is a valid response (no open Slots match).

## `POST /api/v1/patients/clinics/{clinicId}/slots/{slotId}/book`

**Request**:

```json
{ "patientName": "string, required", "appointmentTypeId": "uuid, required" }
```

**Response `201`** — identical shape to 016's `BookingResponse`:

```json
{
  "id": "uuid",
  "slotId": "uuid",
  "patientId": "uuid",
  "appointmentTypeId": "uuid",
  "lockedFee": "300.00",
  "paymentStatus": "PENDING",
  "createdAt": "2026-09-03T10:00:00Z"
}
```

**Errors** (all reuse `com.cms.booking.BookingExceptionHandler`'s existing mappings — no new
exception types needed):

| Status | Code | When |
|---|---|---|
| 401 | `UNAUTHORIZED` | Missing/invalid/expired Patient Account token |
| 404 | `SLOT_NOT_FOUND` | Slot ID doesn't exist, or doesn't belong to `clinicId` |
| 409 | `SLOT_ALREADY_BOOKED` | Slot is not `OPEN` (including a lost concurrent race) |
| 404 | `APPOINTMENT_TYPE_NOT_FOUND` | Appointment type doesn't exist / doesn't belong to the Slot's doctor |
| 409 | `NO_FEE_CONFIGURED` | No fee resolvable for the doctor/appointment-type pairing |

Note: 016's `PATIENT_NOT_FOUND`/`INVALID_MOBILE_NUMBER` errors do not apply here — this
feature never receives a `patientId` or `patientPhone` from the caller (see research.md);
`PatientLinkingService.findOrCreatePatient` cannot fail with "patient not found" since it
always resolves-or-creates from the authenticated `patientAccountId` itself.
