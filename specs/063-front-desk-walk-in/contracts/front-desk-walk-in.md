# Contract: Front-Desk Walk-In Registration (063)

## 1. Register a walk-in (new)

`POST /api/v1/clinics/{clinicId}/walk-ins`. Caller: staff with an active Operations or ClinicAdmin role at the clinic.

```json
{ "sessionId": "uuid",
  "patientId": "uuid | null",
  "patientName": "string | null", "patientPhone": "string | null", "patientEmail": "string | null",
  "appointmentTypeId": "uuid",
  "visitReason": "FEVER_COLD_COUGH | PAIN | FOLLOW_UP | TEST_REPORT_REVIEW | PRESCRIPTION_REFILL | INJURY | GENERAL_CHECKUP | OTHER",
  "visitReasonDetail": "string | null",
  "confirmDuplicate": false }
```

- Give either `patientId`, or `patientName` for a new patient. `patientPhone` and `patientEmail` are optional.

`201 Created`:

```json
{ "bookingId": "uuid", "slotId": "uuid", "sessionId": "uuid", "mode": "FIXED_TIME | QUEUE",
  "tokenNumber": 3, "walkInPosition": 2,
  "patientId": "uuid", "patientName": "string", "doctorName": "string",
  "appointmentTypeId": "uuid", "lockedFee": 450.00,
  "visitReason": "PAIN", "visitReasonDetail": null }
```

- `walkInPosition`: the position in the Fixed-Time walk-in line; `null` for Queue sessions (research.md Decision 7).

Errors (existing shape `{error, message, failedRules, field}`):

| Status | error | When |
|---|---|---|
| 400 | `VISIT_REASON_REQUIRED` | `visitReason` missing or not in the list |
| 400 | `VISIT_REASON_DETAIL_REQUIRED` | `OTHER` without detail, or detail over 200 characters |
| 400 | `PATIENT_REQUIRED` | neither `patientId` nor `patientName` |
| 400 | `INVALID_MOBILE_NUMBER` | existing |
| 400 | `INVALID_EMAIL` | `patientEmail` given but malformed |
| 403 | `FORBIDDEN` | caller is not Operations/ClinicAdmin at the clinic |
| 403 | `CLINIC_NOT_ACTIVE` | existing 062 access gate |
| 404 | `SESSION_NOT_FOUND` / `PATIENT_NOT_FOUND` / `APPOINTMENT_TYPE_NOT_FOUND` | existing |
| 409 | `DUPLICATE_WALK_IN` | patient already waiting in, or booked into, this session and `confirmDuplicate` is false |
| 409 | `NO_FEE_CONFIGURED` | existing |
| 409 | `CLINIC_NOT_ACCEPTING_APPOINTMENTS` | existing 062 |
| 503 | `TOKEN_ISSUANCE_FAILED` | existing: the session's issuance lock could not be obtained within the bound; retry later. *(Documentation fix 2026-09-30, 067: this row said 409, but `BookingExceptionHandler`, the only handler, has always returned 503.)* |

## 2. Session list: two more fields (existing endpoint)

`GET /api/v1/clinics/{clinicId}/sessions?from&to&doctorProfileId`. Each item gains:
- `walkInsWaiting` (int);
- `inWithDoctor` (boolean; always `false` for Queue sessions).

For Fixed-Time sessions, `totalSlotCount`/`bookedSlotCount` now count **timed** slots only.

## 3. Day Sheet: slot rows gain fields (existing endpoint)

`GET /api/v1/clinics/{clinicId}/sessions/{sessionId}/day-sheet`:
- `SlotDetail` (already has `startTime`, `endTime`, `tokenNumber`, `status`) gains `appearedAt` and `completedAt`.
- `BookingDetail` (already has `isWalkIn`) gains `visitReason` and `visitReasonDetail`.

A Fixed-Time walk-in row is recognized by `startTime = null` together with `booking.isWalkIn = true`.

## 4. Existing actions, changed behavior

- `POST /clinics/{c}/slots/{slotId}/appeared` → also stamps `appearedAt`. Accepts an untimed walk-in slot.
- `POST /clinics/{c}/slots/{slotId}/complete` → also stamps `completedAt`. Skips the not-yet-started check for untimed slots.
- `POST /clinics/{c}/bookings/{bookingId}/cancel` (staff) → removes a walk-in from the line. No waitlist offer for untimed slots.
- `POST /patients/bookings/{id}/cancel` (patient) → a walk-in booking returns `409 WALK_IN_NOT_SELF_CANCELLABLE`.

## 5. Retired

`POST /api/v1/clinics/{clinicId}/sessions/{sessionId}/walk-in` (025) is removed. Its frontend route redirects to the front-desk screen.
