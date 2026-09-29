# Contract: Patient Account Signup & Login

## `POST /api/v1/patients/signup`

Public endpoint — no authentication required.

### Request

```json
{
  "email": "string, required, email format",
  "password": "string, required, policy-enforced",
  "mobile": "string, optional, Indian numbering plan"
}
```

### Success Response — `201 Created`

```json
{
  "patientAccountId": "uuid",
  "email": "string"
}
```

Never echoes the password, its hash, or any part of it.

### Error Responses

| Status | Condition | Body shape |
|---|---|---|
| `400 Bad Request` | `password` fails policy | `{ "error": "INVALID_PASSWORD", "message": "...", "failedRules": ["minLength", "uppercase", ...] }` |
| `400 Bad Request` | `mobile` doesn't match the Indian numbering plan | `{ "error": "INVALID_MOBILE_NUMBER", "message": "..." }` |
| `409 Conflict` | `email` already belongs to an existing Patient Account | `{ "error": "EMAIL_ALREADY_IN_USE", "message": "..." }` |
| `400 Bad Request` | Missing required field (`email`, `password`) | `{ "error": "MISSING_REQUIRED_FIELD", "field": "..." }` |
| `500 Internal Server Error` | Any failure during signup after validation passes | `{ "error": "SIGNUP_FAILED" }` |

## `POST /api/v1/patients/login`

Public endpoint.

### Request

```json
{
  "email": "string, required",
  "password": "string, required"
}
```

### Success Response — `200 OK`

```json
{
  "token": "string (JWT, patient-scoped audience)",
  "patientAccountId": "uuid",
  "email": "string"
}
```

### Error Responses

| Status | Condition | Body shape |
|---|---|---|
| `401 Unauthorized` | Email not registered | `{ "error": "ACCOUNT_NOT_FOUND", "message": "..." }` |
| `401 Unauthorized` | Email registered, password incorrect | `{ "error": "INCORRECT_PASSWORD", "message": "..." }` |

> **2026-09-21 update**: these two failure modes originally shared one `INVALID_CREDENTIALS` response (FR-007, "no information leak"). The product owner explicitly directed splitting them into distinct error codes/messages, accepting the resulting user-enumeration tradeoff in exchange for a more specific login error. See `PatientAccountService.authenticate`'s Javadoc.

## Contract Invariants (traced to spec)

- On signup `201`, exactly one Patient Account row exists with `active=true` immediately (FR-005).
- On any signup error response, no Patient Account row is created.
- The signup/login request and response schemas never include a `role`, `clinicId`, or any staff-identity field — this is structurally a different system from 001's clinic/staff registration (FR-008).
- A token issued by `POST /login` MUST NOT be accepted by any staff-only endpoint's authorization check (FR-008) — verified by the JWT's audience/scope claim (see plan.md/research.md), not merely by convention.
- Signing up with an email already used by a staff Account (001) MUST succeed — no cross-system uniqueness check exists (FR-004).
