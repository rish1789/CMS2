# Contract Extension: `POST /api/v1/clinics/{clinicId}/staff` (Doctor path)

This feature extends 004's existing contract (`specs/004-staff-onboarding-direct-hire/contracts/staff-onboarding.md`) — the request shape is unchanged; the response and error set gain the additions below for `role=Doctor` submissions only. `role=Operations` is entirely unaffected.

## Response — `201 Created` (unchanged shape, one new field)

```json
{
  "accountId": "uuid",
  "email": "string",
  "staffCode": "string",
  "temporaryPassword": "string | null",
  "role": "Doctor | Operations",
  "doctorProfileId": "uuid | null",
  "existingAccount": false
}
```

- `existingAccount`: `true` only when this Doctor submission matched an existing Doctor Profile by license number + specialization and reused it (FR-002). `false` in every other case, including all `role=Operations` submissions.
- `temporaryPassword`: `null` when `existingAccount=true` — no new credentials are generated on the reuse branch (FR-002b). Populated as before otherwise.
- `accountId`/`email`/`staffCode`: on the reuse branch, these describe the *existing* Account, not the newly submitted name/email — the submission's own name/email/mobile fields are not persisted on this branch.
- `doctorProfileId`: on the reuse branch, the *existing* Doctor Profile's ID (never a newly created one).

## New Error — Specialization Mismatch

| Status | Condition | Body |
|---|---|---|
| `409 Conflict` | `role=Doctor`, submitted `licenseNumber` matches an existing Doctor Profile, but submitted `specialization` does not match that profile's (case-insensitive, trimmed) (FR-002a) | `{ "error": "SPECIALIZATION_MISMATCH" }` |

No Account, Doctor Profile, or Role Assignment is created when this error is returned.

## Contract Invariants (traced to spec, additive to 004's)

- A Doctor submission whose license number matches no existing profile behaves identically to 004's original contract (`existingAccount=false`, new credentials, new Doctor Profile with `licenseVerified=false`, `visible=true`) — SC-001 (from 004) still holds unchanged.
- A Doctor submission whose license number *and* specialization (case-insensitive, trimmed) both match an existing profile always returns `existingAccount=true`, `temporaryPassword=null`, and creates exactly zero new Account/Doctor Profile rows — only a new Role Assignment (SC-006).
- A Doctor submission whose license number matches but specialization does not always returns `409 SPECIALIZATION_MISMATCH` and creates zero rows of any kind (SC-007).
- The reused Doctor Profile's `licenseVerified` value is bit-for-bit unchanged by a reuse-branch submission (FR-002c, SC-008) — verified by comparing the value immediately before and after the call.
