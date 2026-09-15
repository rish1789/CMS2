# Contract: Super Admin Doctor Profile Edit

Extends `specs/007-doctor-profile-license-queue/contracts/doctor-verification.md`'s resource. Same auth: HTTP Basic Auth with the configured Super Admin credentials, under `/api/v1/admin/**`. A missing/invalid credential returns `401 Unauthorized`.

## `PATCH /api/v1/admin/doctors/{doctorProfileId}`

Edits all four editable Doctor Profile fields together (full-payload, not partial — research.md).

### Request

```json
{
  "specialization": "string, required",
  "licenseNumber": "string, required",
  "experienceYears": "integer, required",
  "visible": "boolean, required"
}
```

### Success Response — `200 OK`

Same shape as 007's `DoctorProfileSummaryResponse` (the list endpoint), reflecting the edit's result — including whether `licenseVerified` reset:

```json
{
  "doctorProfileId": "uuid",
  "accountId": "uuid",
  "specialization": "string",
  "licenseNumber": "string",
  "experienceYears": 0,
  "licenseVerified": false,
  "visible": true
}
```

### Error Responses

| Status | Condition | Body |
|---|---|---|
| `401 Unauthorized` | Missing/invalid Super Admin credentials | — |
| `404 Not Found` | No Doctor Profile with that ID | `{ "error": "DOCTOR_PROFILE_NOT_FOUND" }` |
| `409 Conflict` | The submitted `licenseNumber` already belongs to a *different* Doctor Profile | `{ "error": "DUPLICATE_LICENSE_NUMBER" }` |
| `400 Bad Request` | A required field is missing/blank | standard validation error shape (existing `GlobalExceptionHandler`) |

## Contract Invariants (traced to spec)

- A request whose `licenseNumber` differs from the profile's current value, on a profile that was `licenseVerified: true` before the call, always returns `licenseVerified: false` in the response (FR-003, SC-001).
- A request whose `licenseNumber` is unchanged from the current value never changes `licenseVerified`, regardless of what else in the payload changed (FR-004, SC-002).
- A request editing only `specialization`/`experienceYears`/`visible` (with `licenseNumber` unchanged) never changes `licenseVerified` (FR-005, SC-002).
- No response or side effect from this endpoint ever touches booking/schedule state or triggers 008's cascade (FR-006, SC-003).
- A request whose `licenseNumber` collides with a different Doctor Profile is always rejected `409`, with zero fields on either profile changed (FR-007).
