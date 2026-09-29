# Data Model: Doctor Profile Auto-Creation & License Verification Queue

## DoctorProfile (extends 004's existing entity — `backend/src/main/java/com/cms/identity/doctor/DoctorProfile.java`)

One global row per Account (unchanged invariant from 004, enforced by the existing `uq_doctor_profile_account` constraint).

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | PK, existing |
| `account` | Account (FK, `account_id`) | existing, `unique`, `nullable=false` |
| `specialization` | String | existing, free text, `nullable=false` |
| `licenseNumber` | String | existing, `nullable=false`. **New in this feature**: DB-level `UNIQUE` constraint added (`uq_doctor_profile_license_number`) — see Migration below. |
| `experienceYears` | int | existing, `nullable=false` |
| `licenseVerified` | boolean | existing, defaults `false`. **New in this feature**: gains a `setLicenseVerified(boolean)` mutator so `DoctorVerificationService` can flip it (mirrors `Clinic.setVerified`). Never written by the onboarding-reuse path (research.md). |
| `visible` | boolean | **New field**, `nullable=false`, defaults `true` at construction (FR-003). Gains `isVisible()`/`setVisible(boolean)`; no endpoint in this feature writes it after creation (deferred — spec Assumptions). |
| `createdAt` | Instant | existing |

**Discovery eligibility** (FR-008, read-only concern for this feature — the actual discovery query belongs to 035): a Doctor Profile is eligible for public discovery iff `licenseVerified = true AND visible = true AND` there exists an **active** `RoleAssignment` linking `profile.account` to a `Clinic` with `verified = true`. The `RoleAssignment.active` check is explicit and required (FR-008) — a deactivated Role Assignment (staff deactivation, 007-last-active-clinicadmin-protection) at an otherwise-verified clinic MUST NOT make the doctor eligible via that clinic. This feature is responsible only for `licenseVerified` and `visible` being correct, and for this eligibility relationship being independently queryable — not for writing the discovery query itself.

### Repository additions (`DoctorProfileRepository`)

- `Optional<DoctorProfile> findByLicenseNumber(String licenseNumber)` — the onboarding-time dedup lookup (FR-002).
- `List<DoctorProfile> findByLicenseVerified(boolean licenseVerified)` — backs the Super Admin pending/verified list (FR-004), mirroring `ClinicRepository.findByVerified` (003).

## Migration: `V4__doctor_profile_visibility_and_license_uniqueness.sql`

```sql
ALTER TABLE doctor_profile
    ADD COLUMN visible BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE doctor_profile
    ADD CONSTRAINT uq_doctor_profile_license_number UNIQUE (license_number);
```

Both changes are additive and backward-compatible with 004's existing rows (no data in production yet; `DEFAULT TRUE` covers any pre-existing row regardless). The unique constraint is safe to add now because `StaffOnboardingService`'s new dedup lookup (below) prevents the application from ever attempting to insert a second row with a license number already on file — the constraint is the data-layer backstop for the race case (Constitution Principle IV), not expected to reject any application-level-approved insert.

## Onboarding flow change (`StaffOnboardingService.onboard`, Doctor path only)

Existing flow (unchanged for Operations, and for a Doctor submission with a genuinely new license number):
`validate → check email uniqueness → generate staffCode/temp password → create Account → create RoleAssignment → create DoctorProfile`.

New branch, inserted after `requireDoctorFields` validation and before the email-uniqueness check:

1. `doctorProfileRepository.findByLicenseNumber(request.doctor().licenseNumber())`.
2. **Not found** → proceed exactly as today (email-uniqueness check, new Account, new DoctorProfile with `licenseVerified=false`, `visible=true`, new credentials).
3. **Found**:
   a. Compare `existing.getSpecialization().trim()` to `request.doctor().specialization().trim()`, case-insensitive.
   b. **Mismatch** → throw `SpecializationMismatchException` (400) — no writes at all (FR-002a). The pre-existing email-uniqueness check is skipped entirely on this branch (irrelevant — no new Account is being considered).
   c. **Match** → skip Account/credential generation. Create only a new `RoleAssignment(existing.getAccount(), clinic, Doctor)`. `existing.getAccount()`'s pre-existing `staffCode` is reused (FR-002b). `licenseVerified`/`visible` on `existing` are left untouched (FR-002c). Response: `existingAccount=true`, `temporaryPassword=null`, `staffCode=<existing account's staffCode>`, `doctorProfileId=existing.getId()`.

The DB-level `uq_doctor_profile_license_number` violation (only reachable via a genuine concurrent-race on step 1's read) is caught in the same `catch (DataAccessException e)` block already present, alongside the existing `uq_account_email` check, and translated to `OnboardingFailedException` (the caller should retry — this is an expected-rare race, not a validation error, matching how the existing code treats an equivalent race today).

## Response contract change (`OnboardStaffResponse`)

```java
public record OnboardStaffResponse(
        UUID accountId,
        String email,
        String staffCode,
        String temporaryPassword,   // null on the reuse branch
        String role,
        UUID doctorProfileId,
        boolean existingAccount)    // new field; false except on the reuse branch
```

`email` on the reuse branch is the *existing* Account's email (not the newly submitted one, which is not persisted anywhere on this branch — the submitted name/email/mobile fields are informational-only on a matched submission, since the Account they'd apply to already exists with its own values).

## Super Admin verification endpoints (new — `com.cms.identity.admin`, alongside `ClinicVerificationController`/`Service`)

- `DoctorVerificationService.listByVerified(boolean verified)` → `doctorProfileRepository.findByLicenseVerified(verified)`.
- `DoctorVerificationService.verify(UUID doctorProfileId)` → idempotent `licenseVerified: false → true` (no un-verify action — not in this feature's scope, unlike Clinic's two-way toggle).
- `DoctorProfileNotFoundException` (404) for an unknown `doctorProfileId`, handled by an addition to the existing `AdminExceptionHandler`.

See `contracts/doctor-verification.md` and `contracts/staff-onboarding-extension.md` for the exact request/response shapes.
