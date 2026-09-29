# Contract: Rejected Clinics Stop Operating (062)

No new endpoints. The changes are new error responses on existing endpoints and one added response field.

## 1. Booking refused at a rejected clinic

Applies to:
- `POST /api/v1/patients/clinics/{clinicId}/slots/{slotId}/book` (017, and the 029 waitlist claim, which books through the same service)
- `POST /api/v1/patients/clinics/{clinicId}/sessions/{sessionId}/queue-bookings` (018)
- the staff-assisted fixed-time and queue booking endpoints (016)
- walk-in insertion (020)
- `POST /api/v1/patients/waitlist-entries/{entryId}/claim` (029)

```http
409 Conflict
{ "error": "CLINIC_NOT_ACCEPTING_APPOINTMENTS",
  "message": "This clinic is not accepting appointments.",
  "failedRules": null, "field": null }
```

No booking, patient record, fee lock, or slot-status change is written.

Checked after authentication. For the staff paths, the access gate (§3) refuses non-ClinicAdmin callers first with 403. A ClinicAdmin caller reaches the booking service and gets this 409.

## 2. Patient "My bookings" (`GET /api/v1/patients/bookings`)

Each item gains a field:

```json
{ "...existing fields...": "...",
  "status": "CANCELLED",
  "cancellationReason": "CLINIC_REJECTED" }
```

- `cancellationReason` is `null` for active bookings and for cancellations that carried no reason; it is one of the `BookingCancellationReason` literals otherwise.
- The UI adds the line "This clinic is no longer accepting appointments." under a booking whose reason is `CLINIC_REJECTED`.

The patient cancel endpoint (`POST /api/v1/patients/bookings/{id}/cancel`) refuses `reason: "CLINIC_REJECTED"`:

```http
400 { "error": "INVALID_CANCELLATION_REASON", ... }
```

## 3. Staff access at a rejected clinic

**Sign-in** — `POST /api/v1/staff/login`, for an account whose every active role is Doctor/Operations at a rejected clinic:

```http
403 Forbidden
{ "error": "CLINIC_NOT_ACTIVE",
  "message": "Your clinic is not currently active. Contact your clinic administrator.",
  "failedRules": null, "field": null }
```

ClinicAdmin accounts, accounts with at least one role at a non-rejected clinic, and accounts with no role assignments sign in as today.

**Any clinic-scoped staff request** — `/api/v1/clinics/{clinicId}/**` with a valid staff token, where `{clinicId}` is rejected and the caller has no active ClinicAdmin role there: the same `403 CLINIC_NOT_ACTIVE` body. Tokens issued before the rejection are covered.

**Clinic picker** — `GET /api/v1/clinics/mine` leaves out Doctor/Operations memberships at rejected clinics. ClinicAdmin memberships at a rejected clinic are still listed. `totalCount` reflects the filtered set.

## 4. Session generation

Nightly trigger and `POST` manual trigger (011): the response shape is unchanged. Rejected clinics contribute 0 sessions.
