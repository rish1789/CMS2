# Data Model: Patient Account & Global Login

## Patient Account

The patient's own global self-service login identity. Entirely separate table and identity space from `Account` (staff, 001) — no foreign key or shared uniqueness constraint between them.

| Field | Type | Rules |
|---|---|---|
| `id` | UUID (PK) | Generated |
| `email` | string | Required. Standard email format. **Unique among Patient Accounts** (DB-level unique constraint — FR-003). Independent of `account.email`'s uniqueness (FR-004): the same string may exist in both tables simultaneously. |
| `password_hash` | string | Required. Set from a submitted plaintext password that MUST satisfy the password policy (min 8 chars, ≥1 lowercase, ≥1 uppercase, ≥1 digit, ≥1 special character — FR-002) before hashing (BCrypt). Plaintext never persisted or logged. |
| `mobile` | string | Optional. If present, MUST match the Indian numbering plan (10 digits starting 6–9, optional `+91`/`0` prefix — FR-006). |
| `notification_opt_in` | boolean | Defaults to `true` at creation (opt-out model — the patient can turn it off later; matches common practice for account-level notification preferences). Detailed opt-in/out semantics (per-channel, per-event-type) are owned by 036-notification-event-pipeline-opt-in-out — this feature only guarantees the field exists on this entity with this default, per FR-009. |
| `active` | boolean | Defaults to `true` at creation — active immediately, no verification/pending state (FR-005). |
| `created_at` | timestamp | Set at creation. |

**Relationships**: Patient Account 1─N Patient (the clinic-scoped entity from 019 — not created or touched by this feature; noted here only for context). No relationship to `Account`, `Clinic`, or `RoleAssignment` (001) whatsoever.

**Validation ordering note**: same pattern as 001's `Account.email` — a service-layer pre-check for a fast, friendly error, but the DB-level unique constraint is the actual source of correctness for the concurrent-signup race (Constitution Principle IV).

## State Transitions (in scope for this feature)

- Patient Account: `(none) → active=true` (creation only; deactivation/deletion is out of scope here).

## Out of Scope for This Data Model

- Any relationship, foreign key, or shared field with `Account` (staff, 001) — deliberately absent (FR-004, FR-008).
- The clinic-scoped `Patient` entity itself — created by 019, not this feature; only the eventual Patient Account → Patient relationship is noted above for context.
- Detailed notification-preference structure beyond a single opt-in boolean — owned by 036.
