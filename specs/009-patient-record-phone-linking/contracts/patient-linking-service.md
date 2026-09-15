# Contract: PatientLinkingService (service interface, not a REST endpoint)

Per Constitution Principle III's explicit allowance ("a clear contract — service interface or REST endpoint") and this feature's own Assumptions: no HTTP endpoint exists yet, since neither of this feature's consumers (017, 018) is built. This is the contract those features will code against once they exist.

## `PatientLinkingService.findOrCreatePatient(UUID patientAccountId, UUID clinicId, String name) -> Patient`

Java method signature, `@Transactional`, in `com.cms.patient.record.PatientLinkingService`.

### Parameters

| Name | Required | Notes |
|---|---|---|
| `patientAccountId` | yes | Must reference an existing `PatientAccount` (039) |
| `clinicId` | yes | Must reference an existing `Clinic` (001) — this service does not itself validate clinic existence; a caller passing an unknown id gets whatever `Clinic` FK violation the DB produces (future callers are expected to have already resolved a real clinic, e.g. from a booking request path variable, before calling this) |
| `name` | yes | Used only if a *new* Patient record ends up being created (FR-004); ignored on the existing-link (FR-002) and phone-match (FR-003) paths, where the matched record's own name is authoritative |

### Return

The `Patient` (new or existing) now linked to `patientAccountId` at `clinicId`.

### Exceptions

| Exception | Condition |
|---|---|
| `PatientAccountNotFoundException` | No `PatientAccount` with `patientAccountId` |

No exception is thrown for the race-condition case (FR-006) — a lost race is invisible to the caller; the method simply returns the record the concurrent winner created.

## Contract Invariants (traced to spec)

- Calling this method twice in a row for the same `(patientAccountId, clinicId)` always returns the same `Patient` id both times, and never creates a second row (FR-002, SC-003).
- A `name` argument is only ever observable in the return value when no existing record (linked or unlinked-matching) was found — otherwise the returned `Patient.name` is whatever the existing record already had (FR-002/FR-003).
- The returned `Patient.clinic` always equals the `clinicId` argument; a separate call with a different `clinicId` for the same `patientAccountId` always returns a *different* `Patient` row (FR-007, SC-005).
- Two concurrent calls with the same `(patientAccountId, clinicId, name)` and no pre-existing record both return successfully, and both return the same `Patient` id (FR-005a/FR-006, SC-004).
- A call whose matched phone number belongs to a `Patient` already linked to a *different* `patientAccountId` never returns that record — it returns a newly-created one instead (FR-003, SC-006).
