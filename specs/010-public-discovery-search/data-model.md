# Data Model: Public Discovery Search

No new entity, no new migration. This feature adds one read-only projection and one query over existing tables (`clinic`, `doctor_profile`, `account`, `role_assignment` — all from 001/004/005).

## `DiscoveryResult` (new — read-only DTO/projection, not a JPA `@Entity`)

| Field | Type | Source |
|---|---|---|
| `doctorProfileId` | UUID | `DoctorProfile.id` |
| `doctorName` | String | `Account.name` (the doctor's linked Account) |
| `specialization` | String | `DoctorProfile.specialization` |
| `clinicId` | UUID | `Clinic.id` |
| `clinicName` | String | `Clinic.name` |
| `clinicAddress` | String | `Clinic.address` |

Deliberately excludes: license number, experience years, contact email/mobile, account credentials, any clinical or booking data — none of it is needed for a listing entry, and Constitution Principle IV's privacy framing argues for returning the minimum, not the maximum, available. See spec FR-006/FR-007.

## Eligibility + Match Query (`DiscoveryResultRepository.search(String q)`)

Source: `com.cms.discovery.DiscoveryResultRepository`, joining `RoleAssignment` → `Account` → `DoctorProfile` and `RoleAssignment` → `Clinic`:

**Conditions (all evaluated in SQL, per Constitution Principle IV — see research.md for the full JPQL):**
1. `role_assignment.active = true`
2. `role_assignment.role = 'Doctor'`
3. `clinic.verified = true`
4. `doctor_profile.license_verified = true`
5. `doctor_profile.visible = true`
6. If `q` is present and non-blank: at least one of `specialization`, `account.name`, `clinic.name`, `clinic.address` contains `q` (case-insensitive substring)

**Cardinality**: One result row per `(doctor, clinic)` pair satisfying all six conditions — a doctor holding active Doctor-role Role Assignments at two different *verified* clinics produces two rows (one per clinic); an assignment at an unverified clinic contributes none (Edge Cases).

**Freshness**: Conditions 1–5 read current entity state directly on every call — no cached/denormalized eligibility flag, no re-indexing step. A de-verification (clinic or doctor) is reflected on the very next call (FR-004, SC-002).

## Service layer (`DiscoverySearchService.search(String q)`)

Thin pass-through: trims `q`, treats `null`/blank as "no filter" (passed through as such to the repository — spec Edge Cases: whitespace-only term behaves as no term), returns the repository's `List<DiscoveryResult>` unchanged. No pagination/sorting logic (spec Assumptions — full matching set returned for v1).

## Request/Response contract

See `contracts/discovery-search.md` for the exact HTTP shape. Summary: `GET /api/v1/discovery/search?q={optional text}` returns `200 OK` with a JSON array of `DiscoveryResult`-shaped objects (empty array, never an error, when nothing matches — spec Edge Cases/US2 AC4), with no authentication required or accepted.
