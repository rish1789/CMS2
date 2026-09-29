# Contract: Staff Deactivation

## `POST /api/v1/clinics/{clinicId}/staff/{accountId}/deactivate`

Requires a valid `STAFF`-audience JWT (004) whose Account holds an active `ClinicAdmin` RoleAssignment for `{clinicId}`.

### Request body (employee deactivation modal)

```json
{ "reason": "RESIGNED|SERVICE_NOT_REQUIRED" }
```

Required whenever the target is actually active (see idempotency below) - omitted/blank/unrecognized values are rejected before any write.

### Success — `200 OK`

```json
{ "accountId": "uuid", "clinicId": "uuid", "role": "Doctor|Operations|ClinicAdmin", "active": false }
```

Idempotent: if the target Role Assignment is already `active=false`, this response is returned unchanged with no further side effects - `reason` is not required and not re-validated in that case (nothing is written, and the RoleAssignment's originally-recorded reason is left untouched).

### Errors

| Status | Condition | Body |
|---|---|---|
| `400 Bad Request` | Target is active, but `reason` is missing/blank | `{ "error": "MISSING_REASON", "message": "..." }` |
| `400 Bad Request` | Target is active, but `reason` isn't `RESIGNED` or `SERVICE_NOT_REQUIRED` | `{ "error": "INVALID_REASON", "message": "..." }` |
| `401 Unauthorized` | Missing/invalid staff token | `{ "error": "UNAUTHORIZED" }` |
| `403 Forbidden` | Valid token, but not an active ClinicAdmin for `{clinicId}` | `{ "error": "FORBIDDEN" }` |
| `404 Not Found` | No such Role Assignment for `{accountId}` at `{clinicId}` | `{ "error": "NOT_FOUND" }` |
| `409 Conflict` | Target is a `ClinicAdmin` Role Assignment and is the clinic's only active one | `{ "error": "LAST_ACTIVE_CLINIC_ADMIN", "message": "..." }` — **no override, for any caller, including Super Admin** (Super Admin has no path to call this endpoint at all — it's staff-JWT-only) |

## Contract Invariants (traced to spec)

- A `409 LAST_ACTIVE_CLINIC_ADMIN` response always leaves the target Role Assignment `active=true`, unchanged (FR-004).
- The last-active check only ever applies to `role=ClinicAdmin` targets — a Doctor/Operations deactivation is never blocked by it (FR-001, FR-005).
- The check is scoped to `{clinicId}` only — deactivating the last ClinicAdmin at one clinic is never affected by ClinicAdmin counts at any other clinic (FR-008).

## `POST /api/v1/clinics/{clinicId}/staff/{accountId}/reset-password`

real-bug-fix 2026-09-17: lets a ClinicAdmin recover a locked-out Doctor or Operations staff
member's login at their own clinic — previously only Super Admin could reset a password, and
only for a ClinicAdmin's own account (`/api/v1/admin/clinics/{clinicId}/reset-admin-password`).
Same auth gate as `/deactivate` above. Generates a random password (`TemporaryPasswordGenerator`,
the same one staff onboarding uses); the new password is returned exactly once, never stored or
logged in plaintext.

### Success — `200 OK`

```json
{ "accountId": "uuid", "email": "string", "staffCode": "string", "temporaryPassword": "string" }
```

## `POST /api/v1/clinics/{clinicId}/staff/{accountId}/set-password`

Same gate and target scoping as `reset-password` above, but lets the ClinicAdmin choose a
specific password instead of a random one — still policy-checked (`PasswordPolicyValidator`,
the same one clinic registration uses).

### Request body

```json
{ "newPassword": "string" }
```

### Success — `200 OK`

Same shape as `reset-password` above, with `temporaryPassword` echoing the caller's own choice.

### Errors (both endpoints)

| Status | Condition | Body |
|---|---|---|
| `401 Unauthorized` | Missing/invalid staff token | `{ "error": "UNAUTHORIZED" }` |
| `403 Forbidden` | Valid token, but not an active ClinicAdmin for `{clinicId}` | `{ "error": "FORBIDDEN" }` |
| `403 Forbidden` | Target Role Assignment's role is `ClinicAdmin` — **no override**, resetting a fellow ClinicAdmin's password stays Super-Admin-only (a password reset is a silent full account takeover, a materially more sensitive action than deactivation) | `{ "error": "FORBIDDEN" }` |
| `404 Not Found` | No such Role Assignment for `{accountId}` at `{clinicId}` | `{ "error": "NOT_FOUND" }` |
| `400 Bad Request` | `set-password` only: `newPassword` fails `PasswordPolicyValidator` | `{ "error": "INVALID_PASSWORD", "failedRules": ["..."] }` |
