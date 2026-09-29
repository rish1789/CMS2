# Contract: Clinic Portal Login (extended)

## `POST /api/v1/staff/login`

**Changed from `006-staff-login-dual-identifier`**: this endpoint now also resolves Super Admin credentials, and the response gains a `role` field. Request shape is unchanged.

### Request
```json
{ "identifier": "string, required (email, staff code, or the configured Super Admin username)", "password": "string, required" }
```

### Resolution order (FR-003)
1. If `identifier`/`password` match the configured Super Admin credential → authenticate as Super Admin.
2. Else if `identifier` matches the configured Super Admin username (but the password didn't match in step 1) → `401 Unauthorized`, `INCORRECT_PASSWORD`.
3. Else if `identifier` matches an Account's email or staff code:
   - and `password` matches that Account → authenticate as staff.
   - and `password` does not match → `401 Unauthorized`, `INCORRECT_PASSWORD`.
4. Else → `401 Unauthorized`, `ACCOUNT_NOT_FOUND`.

> **2026-09-21 update**: steps 2-4 originally shared one `INVALID_CREDENTIALS` response (FR-004, "no information leak"). The product owner explicitly directed splitting "no matching identifier" from "identifier matched, wrong password" into distinct error codes/messages, accepting the resulting user-enumeration tradeoff in exchange for a more specific login error. See `StaffAuthService.login`'s Javadoc.

### Success — `200 OK`, resolved as Super Admin
```json
{ "token": "string (JWT, SUPER_ADMIN audience)", "accountId": null, "email": "string (the configured Super Admin username)", "role": "SUPER_ADMIN" }
```

### Success — `200 OK`, resolved as staff
```json
{ "token": "string (JWT, STAFF audience)", "accountId": "uuid", "email": "string", "role": "STAFF" }
```
Identical to `006`'s existing response shape plus the new `role: "STAFF"` field — no other change for the staff path.

### Errors
| Status | Condition | Body shape |
|---|---|---|
| `401 Unauthorized` | `identifier` matched no Super Admin username, staff email, or staff code | `{ "error": "ACCOUNT_NOT_FOUND", "message": "..." }` |
| `401 Unauthorized` | `identifier` matched an identity (Super Admin or staff), but `password` was wrong | `{ "error": "INCORRECT_PASSWORD", "message": "..." }` |

## Contract Invariants (traced to spec)

- A caller *can* distinguish "no matching identifier at all" from "identifier recognized, wrong password" (`ACCOUNT_NOT_FOUND` vs `INCORRECT_PASSWORD`) — this was originally a deliberate non-distinction (FR-004/FR-010's "no information leak" design), superseded 2026-09-21 by explicit product direction. A wrong password for the Super Admin username specifically still reports `INCORRECT_PASSWORD`, not `ACCOUNT_NOT_FOUND` - the staff Account lookup that follows a failed Super Admin match can never itself match the Super Admin username, so this is checked directly rather than left to fall through and misreport.
- `role` is always present and is always exactly one of `"STAFF"` / `"SUPER_ADMIN"` on a `200` response — the frontend's post-login redirect (FR-005/FR-006/FR-011) branches on this field alone, never on token content.

---

# Contract: Super Admin Console Endpoints

## `/api/v1/admin/**` (all existing endpoints under this prefix, e.g. `GET/POST /api/v1/admin/clinics`, `/api/v1/admin/doctors`, `/api/v1/admin/sessions/generate`)

**Changed**: authentication mechanism only. Endpoint paths, request/response bodies, and business behavior of every existing `/api/v1/admin/**` endpoint are unchanged.

### Before this feature
`Authorization: Basic <base64(username:password)>`, re-verified on every request against the configured Super Admin credential.

### After this feature
`Authorization: Bearer <token>`, where `token` is a Super Admin JWT issued by `POST /api/v1/staff/login` (above). Basic Auth is no longer accepted.

### Errors
| Status | Condition |
|---|---|
| `401 Unauthorized` | Missing `Authorization` header, malformed bearer token, expired Super Admin JWT, or a syntactically valid token that is not a Super Admin token (e.g. a staff or patient JWT) — all identical: `{"error":"UNAUTHORIZED"}` (FR-009/FR-010, mirrors `StaffAuthenticationEntryPoint`'s existing body shape) |

## Contract Invariants (traced to spec)

- A request carrying a valid staff JWT or patient JWT to any `/api/v1/admin/**` endpoint is rejected exactly the same way as a request with no credential at all (FR-009) — the filter only recognizes its own `SUPER_ADMIN`-audience token, so a token from a different identity system simply never populates the security context, the same structural guarantee `StaffJwtAuthenticationFilter`/`PatientJwtAuthenticationFilter` already provide for their own audiences.
- No `/api/v1/admin/**` response body or status code differs between "no credential" and "wrong-identity credential" (FR-010) — both hit the same `SecurityContextHolder`-empty → `.authenticated()` → entry-point path.
