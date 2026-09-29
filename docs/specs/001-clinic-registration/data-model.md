# Data Model: Clinic Registration

## Clinic

The registering business. Created once, atomically, alongside its founding ClinicAdmin's Account and Role Assignment.

| Field | Type | Rules |
|---|---|---|
| `id` | UUID (PK) | Generated |
| `name` | string | Required, non-empty. NOT required to be globally unique (two clinics may share a name — resolved assumption). |
| `address` | string | Required, non-empty. No further format constraint specified. |
| `contact_email` | string | Optional. Standard email format if present. Distinct from the ClinicAdmin's login email — a clinic's public contact email need not match its admin's login. |
| `contact_mobile` | string | Optional. If present, MUST match the Indian numbering plan: 10 digits starting 6–9, with an optional `+91` or `0` prefix (FR-010). |
| `verified` | boolean | Defaults to `false` at creation. This feature only ever writes `false` here — flipping it to `true` is 002-super-admin-clinic-verification's responsibility, out of scope for this feature. |
| `created_at` | timestamp | Set at creation. |

**Relationships**: Clinic 1─N Role Assignment (its ClinicAdmin, and later Doctor/Operations staff via other features). Clinic 1─1 (at creation) its founding ClinicAdmin's Role Assignment.

## Account

The login identity created for the founding ClinicAdmin. (This is the same `Account` entity later features — 003, 004 — extend for other staff roles; this feature only covers its creation for the founding admin.)

| Field | Type | Rules |
|---|---|---|
| `id` | UUID (PK) | Generated |
| `name` | string | Required, non-empty. |
| `email` | string | Required. Standard email format. **Globally unique across the entire platform** — enforced by a DB-level unique constraint (FR-012, Clarifications). A registration attempt with an existing email is rejected before any write occurs. |
| `password_hash` | string | Required. Set from a submitted plaintext password that MUST satisfy the password policy (min 8 chars, ≥1 lowercase, ≥1 uppercase, ≥1 digit, ≥1 special character — FR-009) before hashing (BCrypt). Plaintext is never persisted or logged. |
| `staff_code` | string | Required. System-generated at creation (e.g. `CA-4821`-style), **globally unique across the entire platform** — same generation/uniqueness mechanism as 004-staff-onboarding-direct-hire (FR-013, Clarifications). An alternate login identifier for this same Account. |
| `mobile` | string | Optional. If present, MUST match the Indian numbering plan (same rule as Clinic.contact_mobile). |
| `active` | boolean | Defaults to `true` at creation — the founding ClinicAdmin is active immediately, no pending/awaiting-acceptance state. |
| `created_at` | timestamp | Set at creation. |

**Validation ordering note**: email-uniqueness and staff-code-uniqueness checks are enforced by DB constraints, not just a pre-check query, specifically to close the race where two simultaneous registrations submit the same email (Constitution Principle IV) — the service layer should still perform an early check for a fast, friendly error, but MUST NOT rely on it alone for correctness.

## Role Assignment

Links an Account to a Clinic with a role. This feature only ever creates one, with role `ClinicAdmin`.

| Field | Type | Rules |
|---|---|---|
| `id` | UUID (PK) | Generated |
| `account_id` | UUID (FK → Account) | Required. |
| `clinic_id` | UUID (FK → Clinic) | Required. |
| `role` | enum (`ClinicAdmin`, `Doctor`, `Operations`) | This feature only ever writes `ClinicAdmin`. The other values are used by later features (004). |
| `active` | boolean | Defaults to `true` at creation. |
| `created_at` | timestamp | Set at creation. |

**Invariant enforced by this feature**: a Clinic row and its founding `ClinicAdmin` Role Assignment row are created in the same transaction — there is no committed state where one exists without the other (FR-002, FR-003). This is the creation-time half of the "last active ClinicAdmin can never be removed" invariant maintained continuously by 007-last-active-clinicadmin-protection.

## State Transitions (in scope for this feature)

- Clinic: `(none) → verified=false` (creation only; this feature never transitions `verified` further).
- Account: `(none) → active=true` (creation only; deactivation is out of scope here — see 007).
- Role Assignment: `(none) → active=true, role=ClinicAdmin` (creation only).

## Out of Scope for This Data Model

- Clinic verification transition (`verified: false → true`) — 002.
- Any Grievance Officer, billing/subscription, or file/document fields — explicitly excluded (FR-006, FR-007, FR-008).
