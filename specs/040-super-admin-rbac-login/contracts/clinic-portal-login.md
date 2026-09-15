# Contract: Clinic Portal Login (extended)

## `POST /api/v1/staff/login`

**Changed from `006-staff-login-dual-identifier`**: this endpoint now also resolves Super Admin credentials, and the response gains a `role` field. Request shape is unchanged.

### Request
```json
{ "identifier": "string, required (email, staff code, or the configured Super Admin username)", "password": "string, required" }
```

### Resolution order (FR-003)
1. If `identifier`/`password` match the configured Super Admin credential → authenticate as Super Admin.
2. Else if `identifier` matches an Account's email or staff code, and `password` matches that Account → authenticate as staff.
3. Else → `401 Unauthorized`, `INVALID_CREDENTIALS` (identical response for all three ways this can fail — FR-004; unchanged from `006`).

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
| Status | Condition |
|---|---|
| `401 Unauthorized` | `identifier`/`password` matched neither the Super Admin credential nor any staff Account, or matched an identity but the password was wrong — one shared response for all cases (FR-004) |

## Contract Invariants (traced to spec)

- A caller cannot distinguish "not a recognized Super Admin credential" from "not a recognized staff identifier" from "identifier recognized, wrong password" — all three produce the exact same `401 INVALID_CREDENTIALS` body (FR-004, FR-010).
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
