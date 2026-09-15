# Contract: Explicit Doctor License Revoke (User Story 2's necessary prerequisite)

The cascade itself (both User Stories) has **no** HTTP contract — it is only ever invoked
internally by `DeVerificationCascadeListener` reacting to `ClinicDeVerifiedEvent` (existing) or
`DoctorLicenseRevokedEvent` (new). This contract covers only the new admin action that makes
Trigger 2 possible.

## `POST /api/v1/admin/doctors/{doctorProfileId}/revoke`

Requires Super Admin HTTP Basic Auth (falls under `SuperAdminSecurityConfig`'s existing blanket
`/api/v1/admin/**` chain — no new matcher needed, research.md R7).

### Request

No body.

### Success Response — `200 OK`

Identical shape to the existing `/verify` endpoint's response
(`com.cms.identity.admin.dto.DoctorVerificationStatusResponse`, reused as-is):

```json
{ "doctorProfileId": "uuid", "licenseVerified": false }
```

Idempotent: calling this against an already-unverified doctor returns the same `200` shape
(`licenseVerified: false`) with no second cascade triggered (FR-009) — mirrors the existing
`/verify` endpoint's own idempotent behavior exactly, just in the opposite direction.

### Error Responses

| Status | Condition | Body `error` |
|---|---|---|
| `401 Unauthorized` | Missing/invalid Super Admin credentials | — |
| `404 Not Found` | No `DoctorProfile` with `doctorProfileId` | `DOCTOR_PROFILE_NOT_FOUND` |

## Contract Invariants (traced to spec)

- A genuine `true -> false` transition always publishes exactly one `DoctorLicenseRevokedEvent`
  (FR-002/FR-003).
- 006's automatic edit-triggered reset never calls this action, and never publishes this event
  (FR-002/FR-009, SC-005).
