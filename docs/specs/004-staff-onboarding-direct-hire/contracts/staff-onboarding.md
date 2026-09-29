# Contract: Staff Login & Onboarding

## `POST /api/v1/staff/login`

Public endpoint. Email + password only in this feature (the staff-code alternate identifier is added by 003).

### Request
```json
{ "email": "string, required", "password": "string, required" }
```

### Success — `200 OK`
```json
{ "token": "string (JWT, STAFF audience)", "accountId": "uuid", "email": "string" }
```

### Errors
| Status | Condition |
|---|---|
| `401 Unauthorized` | Unknown email OR wrong password — identical response shape for both (no information leak, mirrors 002's FR-007 pattern) |

## `POST /api/v1/clinics/{clinicId}/staff`

Requires a valid `STAFF`-audience JWT (from the endpoint above) whose Account holds an active `ClinicAdmin` RoleAssignment for `{clinicId}`.

### Request
```json
{
  "name": "string, required",
  "email": "string, required",
  "mobile": "string, optional, Indian numbering plan",
  "role": "Doctor | Operations, required",
  "doctor": {
    "specialization": "string, required if role=Doctor",
    "licenseNumber": "string, required if role=Doctor",
    "experienceYears": "integer, required if role=Doctor"
  }
}
```

### Success — `201 Created`
```json
{
  "accountId": "uuid",
  "email": "string",
  "staffCode": "string (e.g. DR-4821 / OP-1234)",
  "temporaryPassword": "string (shown once, never retrievable again)",
  "role": "Doctor | Operations",
  "doctorProfileId": "uuid | null"
}
```

### Errors
| Status | Condition | Body |
|---|---|---|
| `401 Unauthorized` | Missing/invalid staff token | `{ "error": "UNAUTHORIZED" }` |
| `403 Forbidden` | Valid token, but not a ClinicAdmin for `{clinicId}` | `{ "error": "FORBIDDEN" }` |
| `400 Bad Request` | `role` is anything other than `Doctor`/`Operations` (including `ClinicAdmin`/`SuperAdmin`) — rejected even if somehow sent | `{ "error": "INVALID_ROLE" }` |
| `400 Bad Request` | `role=Doctor` but doctor fields missing | `{ "error": "MISSING_REQUIRED_FIELD", "field": "..." }` |
| `400 Bad Request` | `mobile` invalid format | `{ "error": "INVALID_MOBILE_NUMBER" }` |
| `409 Conflict` | `email` already in use by any staff Account | `{ "error": "EMAIL_ALREADY_IN_USE" }` |
| `500 Internal Server Error` | Any failure during the atomic create | `{ "error": "ONBOARDING_FAILED" }` — full rollback, no partial rows |

## Contract Invariants (traced to spec)

- `role=ClinicAdmin` or `role=SuperAdmin` is always rejected as `400 INVALID_ROLE`, never accepted, regardless of what the client sends (FR-003).
- A `201` for `role=Doctor` always includes a non-null `doctorProfileId`; for `role=Operations`, it's always `null` (FR-007, SC-004).
- `temporaryPassword` in the response always satisfies the password policy (FR-006).
- No field in the request/response schema initiates or references an email/SMS send (FR-005).
