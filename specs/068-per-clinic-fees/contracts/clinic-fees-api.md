# Contract: Clinic-Scoped Fees API (068)

All endpoints sit on the **staff** chain (`/api/v1/clinics/**`) and need a staff Bearer token. That chain already requires authentication for everything except clinic registration, so no allowlist change is needed.

**Common errors**
- 401: no or invalid token.
- 403 `FORBIDDEN`: not authorized for this clinic.
- 404: the doctor or appointment type is unknown, or the type is not the doctor's.

## GET `/api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/fees`

**Access:** any active staff member of `clinicId` (FR-006).

```json
200 {
  "clinicId": "…", "doctorProfileId": "…",
  "defaultFee": 500.00,                       // null when the clinic has none
  "appointmentTypes": [
    { "appointmentTypeId": "…", "name": "Consultation", "price": null, "effectiveFee": 500.00 },
    { "appointmentTypeId": "…", "name": "Procedure",    "price": 800.00, "effectiveFee": 800.00 }
  ]
}
```

`effectiveFee` is null when the type is not bookable at this clinic (no price and no default).

## PUT `/api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/fees/default`

**Access:** an active **ClinicAdmin of `clinicId`** only (FR-005). The doctor must be actively staffed at `clinicId`; otherwise the request is refused with 403 `FORBIDDEN`.

- **Body:** `{ "amount": 500.00 }`, where `amount` is at least 0, with at most 2 decimals.
- **Responses:**
  - 200: the updated GET payload.
  - 400 `INVALID_FEE_AMOUNT`: the amount is negative or malformed.

## PUT `/api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/fees/appointment-types/{appointmentTypeId}`

**Access:** as above. The body and responses are the same as for the default fee. It sets this type's price at this clinic.

## DELETE `/api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/fees/appointment-types/{appointmentTypeId}`

**Access:** as above. It removes this type's price at this clinic, so the clinic default applies again.

- **Responses:** 200 with the updated GET payload. Deleting a price that does not exist still returns 200 (idempotent).

## Retired doctor-wide price writes (FR-012)

| Request | Response |
|---|---|
| `PUT /api/v1/doctors/{id}/default-fee` | **410** `FEE_MOVED_TO_CLINIC` |
| `POST /api/v1/doctors/{id}/appointment-types` with a non-null `feeOverride` | **400** `FEE_MOVED_TO_CLINIC` |

Creating a type without a fee, renaming and listing are unchanged.

## Clinic-scoped appointment types for booking

`GET /api/v1/patients/clinics/{clinicId}/doctors/{doctorProfileId}/appointment-types` returns 200 `[{ id, doctorProfileId, name, fee }]`. `fee` is the effective price at `clinicId`, or null when not bookable there.

- `AppointmentTypeResponse` changes from `feeOverride` to `fee`: the effective price in the response's clinic context.
- Clinic-scoped patient listings (open slots, queue sessions) embed it the same way.
- **Compatibility:** the old `GET /api/v1/patients/doctors/{id}/appointment-types` returns names with `fee: null`. It has no clinic context, so it cannot state a price.
