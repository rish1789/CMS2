# Data Model: Staff Onboarding (Direct-Hire)

## Account, Role Assignment (reused from 001, no schema change)

New rows created here follow exactly the shape 001 already established: `Account` (globally unique `email` and `staff_code`, hashed password) and `RoleAssignment` (`account_id`, `clinic_id`, `role` — the `role` enum already includes `Doctor`/`Operations` from 001's migration). This feature writes to these existing tables; it does not alter their schema.

## Doctor Profile (new)

Created only for the Doctor onboarding path, in the same transaction as the Account/RoleAssignment above.

| Field | Type | Rules |
|---|---|---|
| `id` | UUID (PK) | Generated |
| `account_id` | UUID (FK → account) | Required. One Doctor Profile per Account (the Doctor's global identity, per BDD — one Doctor Profile per Account across the whole platform, not per clinic). |
| `specialization` | string | Required for Doctor onboarding. |
| `license_number` | string | Required for Doctor onboarding. |
| `experience_years` | integer | Required for Doctor onboarding. |
| `license_verified` | boolean | Defaults to `false` at creation — awaiting verification (FR-007). The verification *workflow* itself (list/verify) is 005's responsibility, not built here. |
| `created_at` | timestamp | Set at creation. |

**Ownership note**: this feature creates the row; 005-doctor-profile-auto-creation-license-queue builds the admin verification workflow on top of the same table (research.md).

## State Transitions (in scope for this feature)

- Account: `(none) → active=true` (creation only, mirrors 001).
- RoleAssignment: `(none) → active=true, role=Doctor|Operations` (creation only).
- DoctorProfile: `(none) → licenseVerified=false` (creation only; the `false → true` transition belongs to 005).

## Out of Scope for This Data Model

- Any `license_verified: false → true` transition or the admin screen driving it — 005.
- Doctor Profile edits after creation (e.g. license-number changes triggering re-verification) — 006-doctor-license-edit-reverification-reset.
