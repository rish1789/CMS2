# Contract: Register Clinic

`POST /api/v1/clinics/register`

Public endpoint — no authentication required (this *creates* the first authenticated identity for the clinic).

## Request

```json
{
  "clinic": {
    "name": "string, required",
    "address": "string, required",
    "contactEmail": "string, optional, email format",
    "contactMobile": "string, optional, Indian numbering plan"
  },
  "admin": {
    "name": "string, required",
    "email": "string, required, email format",
    "password": "string, required, policy-enforced",
    "mobile": "string, optional, Indian numbering plan"
  }
}
```

## Success Response — `201 Created`

```json
{
  "clinicId": "uuid",
  "clinicName": "string",
  "verified": false,
  "admin": {
    "accountId": "uuid",
    "email": "string",
    "staffCode": "string (e.g. CA-4821)"
  }
}
```

Note: the response never echoes the password, its hash, or any part of it.

## Error Responses

| Status | Condition | Body shape |
|---|---|---|
| `400 Bad Request` | `admin.password` fails policy | `{ "error": "INVALID_PASSWORD", "message": "...", "failedRules": ["minLength", "uppercase", ...] }` — identifies which specific rule(s) failed (FR-009) |
| `400 Bad Request` | `clinic.contactMobile` or `admin.mobile` doesn't match the Indian numbering plan | `{ "error": "INVALID_MOBILE_NUMBER", "field": "clinic.contactMobile" \| "admin.mobile", "message": "..." }` |
| `409 Conflict` | `admin.email` already belongs to an existing staff Account anywhere on the platform | `{ "error": "EMAIL_ALREADY_IN_USE", "message": "..." }` (FR-012) |
| `400 Bad Request` | Missing required field (`clinic.name`, `clinic.address`, `admin.name`, `admin.email`, `admin.password`) | `{ "error": "MISSING_REQUIRED_FIELD", "field": "..." }` |
| `500 Internal Server Error` | Any failure during the atomic create transaction (after all validation passes) | `{ "error": "REGISTRATION_FAILED" }` — transaction is rolled back; no Clinic, Account, or Role Assignment row is left behind (FR-003) |

## Contract Invariants (traced to spec)

- On `201`, a Clinic row, an Account row, and a `ClinicAdmin` Role Assignment row all exist, in the same transaction (FR-002).
- On any error response, none of those three rows exist — no partial state (FR-003).
- `verified` is always `false` in the success response body — this endpoint never returns a verified clinic (FR-004).
- The response's `admin.staffCode` is always present and non-empty — this endpoint always generates one (FR-013).
- No field for a Grievance Officer, billing/payment, or file upload exists anywhere in the request or response schema (FR-006, FR-007, FR-008).
