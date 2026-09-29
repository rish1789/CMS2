# Phase 1 Data Model: Super Admin RBAC Login & Console Access

No new database entity and no Flyway migration — this feature secures access and issues a token; it does not add persisted state. (Confirmed against Constitution IV: no patient-identifying or clinical data is touched.)

## Super Admin Session *(client-side + token concept only — not a DB row)*

Represents "this browser tab is currently authenticated as Super Admin."

| Field | Type | Notes |
|---|---|---|
| `token` | JWT string | Issued by `SuperAdminJwtService.issueToken(username)`. Audience claim `SUPER_ADMIN`, subject = configured Super Admin username, 12-hour expiry (same TTL as the staff JWT). Signed with a dedicated secret (`admin.super-admin.jwt.secret`), never shared with the staff (`staff.jwt.secret`) or patient (`patient.jwt.secret`) signing keys — same "no shared signing key between identity systems" invariant `002`/`039` already established for each other. |
| `username` | string | The Super Admin's configured username, stored client-side alongside the token purely for display (e.g. "Signed in as \<username\>"); never re-validated from this stored copy — every request re-sends the token, which the backend re-verifies. |

Stored in `sessionStorage` (key `cms.superAdminToken`), mirroring `staff-login/token.ts`'s `StoredStaffSession` and `patient-account/token.ts`'s equivalent exactly — a credential-bearing session, not app state, so it does not survive beyond the tab (existing codebase convention).

## `StaffLoginResponse` (extended, not a new type)

`com.cms.identity.account.dto.StaffLoginResponse` — existing record, one field added:

| Field | Type | Staff-resolved login | Super-Admin-resolved login |
|---|---|---|---|
| `token` | `String` | Staff JWT (audience `staff`) | Super Admin JWT (audience `SUPER_ADMIN`) |
| `accountId` | `UUID` (now nullable) | The Account's ID | `null` — no DB row exists for Super Admin |
| `email` | `String` | The Account's email | The Super Admin's configured username (field repurposed as a generic "identity label," never parsed as an email by any consumer) |
| `role` *(new)* | `String` | `"STAFF"` | `"SUPER_ADMIN"` |

`StaffLoginRequest` is unchanged (`identifier`, `password`) — the same two fields already serve both cases (research.md R1).

## Relationships / lifecycle

- No relationship to `Account`, `RoleAssignment`, or `PatientAccount` — the Super Admin identity remains exactly what `002-super-admin-clinic-verification` defined it as (a single config-bootstrapped credential pair, FR-004/FR-009 of that feature), unchanged by this feature. This feature only changes *how that credential is verified and remembered* (a JWT instead of per-request Basic Auth), never *how many Super Admins exist or how they're provisioned*.
- No state transitions — a Super Admin Session is either present (valid, unexpired token in `sessionStorage`) or absent; it is never partially valid. Expiry is enforced entirely by JWT `exp` validation in `SuperAdminJwtService.validateAndGetUsername`, identical in mechanism to the existing staff/patient token validation.
