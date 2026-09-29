# Contract: Patient Anonymization (User Story 1)

## `POST /api/v1/clinics/{clinicId}/patients/{patientId}/anonymize`

Requires a valid staff bearer token with an active `Operations` or `ClinicAdmin` role at
`clinicId` (research.md R7).

### Request

No body.

### Success Response — `200 OK`

```json
{ "patientId": "uuid", "anonymized": true, "anonymizedAt": "2026-09-04T10:00:00Z" }
```

Idempotent: a repeated call against an already-anonymized patient returns the same shape with the
*original* `anonymizedAt` (FR-007) — never a `409`.

### Error Responses

| Status | Condition | Body `error` |
|---|---|---|
| `401 Unauthorized` | Missing/invalid staff bearer token | — |
| `403 Forbidden` | Caller has no active Operations/ClinicAdmin role at `clinicId` | `FORBIDDEN` |
| `404 Not Found` | No `Patient` with `patientId` at `clinicId` | `PATIENT_NOT_FOUND` |
| `409 Conflict` | The patient has at least one active future booking | `PATIENT_HAS_ACTIVE_FUTURE_BOOKING` |

## Contract Invariants (traced to spec)

- A successful anonymization always clears `name`/`phone` and stamps `anonymizedAt` exactly once
  (FR-002/FR-004).
- A blocked attempt modifies no fields at all (FR-003, SC-002).
- The patient's linked Patient Account, and every Booking/clinical-documentation record
  referencing this Patient, are never touched by this endpoint (FR-005/FR-006).
