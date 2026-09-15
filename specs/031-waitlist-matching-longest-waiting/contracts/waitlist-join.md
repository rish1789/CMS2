# Contract: Waitlist Join (User Story 2)

Matching itself (User Story 1) has **no** HTTP contract — it is only ever invoked internally by
`WaitlistBumpListener` reacting to 025's `BookingCancelledEvent` (FR-009). This contract covers
the join action only, the necessary prerequisite.

## `POST /api/v1/patients/clinics/{clinicId}/waitlist` (patient self-service)

Requires a valid patient bearer token (`/api/v1/patients/**` chain).

### Request

Doctor-match:
```json
{ "doctorProfileId": "uuid" }
```
Specialization-only:
```json
{ "specialization": "Cardiology" }
```

### Success Response — `201 Created`

```json
{
  "id": "uuid",
  "clinicId": "uuid",
  "doctorProfileId": "uuid | null",
  "specialization": "string | null",
  "status": "WAITING",
  "joinedAt": "2026-09-04T10:00:00Z"
}
```

### Error Responses

| Status | Condition | Body `error` |
|---|---|---|
| `401 Unauthorized` | Missing/invalid patient bearer token | — |
| `404 Not Found` | No `Clinic` with `clinicId` | `CLINIC_NOT_FOUND` |
| `404 Not Found` | `doctorProfileId` given but not staffed at `clinicId` | `DOCTOR_NOT_STAFFED_AT_CLINIC` |
| `400 Bad Request` | Neither `doctorProfileId` nor `specialization` given | `WAITLIST_TARGET_REQUIRED` |

## `POST /api/v1/clinics/{clinicId}/waitlist` (staff, on a patient's behalf)

Requires a valid staff bearer token (`/api/v1/clinics/**` chain). Any active Operations staff
member or ClinicAdmin at the clinic may join a patient — never the Doctor (mirrors this
codebase's standard write-action gate).

### Request

Same as the patient endpoint, plus a required `patientAccountId`:
```json
{ "patientAccountId": "uuid", "doctorProfileId": "uuid" }
```

### Success/Error Responses

Identical shape to the patient endpoint above, plus:

| Status | Condition | Body `error` |
|---|---|---|
| `403 Forbidden` | Caller is neither an active Operations nor ClinicAdmin at `clinicId` | `FORBIDDEN` |
| `404 Not Found` | No `PatientAccount` with `patientAccountId` | `PATIENT_ACCOUNT_NOT_FOUND` |

## Contract Invariants (traced to spec)

- Every created entry has exactly one of `doctorProfileId`/`specialization` set, never both,
  never neither (FR-001/FR-002/FR-003).
- `status` is always `WAITING` immediately after joining.
- The staff and patient endpoints produce byte-identical entry shapes for the same target
  (User Story 2, Acceptance Scenario 3).
