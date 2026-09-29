# Data Model: Doctor License Edit Triggers Re-Verification Reset

## DoctorProfile (extends 004/007's existing entity — no schema change)

No new columns, no migration. This feature adds three entity mutators so the existing fields can be edited:

| Field | Type | Change in this feature |
|---|---|---|
| `specialization` | String | **New**: `setSpecialization(String)` |
| `licenseNumber` | String | **New**: `setLicenseNumber(String)` |
| `experienceYears` | int | **New**: `setExperienceYears(int)` |
| `licenseVerified` | boolean | Unchanged — `setLicenseVerified(boolean)` already exists (007), used here for the reset |
| `visible` | boolean | Unchanged — `setVisible(boolean)` already exists (007), now gets its first caller |

## Edit flow (`DoctorVerificationService.edit`, new method)

1. Load the `DoctorProfile` by id, or `DoctorProfileNotFoundException` (404, existing exception from 007, reused).
2. Compare the submitted `licenseNumber` to `profile.getLicenseNumber()` (exact match — this feature does not extend 007's case-insensitive/trimmed comparison, since that rule was specifically for *matching an existing doctor at onboarding*, a different concern from *this profile's own field changing*).
3. Apply all four submitted field values (`specialization`, `licenseNumber`, `experienceYears`, `visible`) to the entity.
4. If the license number changed (step 2 found a difference) **and** `profile.isLicenseVerified()` was `true` before this edit, call `profile.setLicenseVerified(false)` (FR-003). No event is published (research.md — deliberately not the 003/`ClinicDeVerifiedEvent` pattern, per FR-006).
5. Save. A `uq_doctor_profile_license_number` violation (the submitted license number now collides with a *different* Doctor Profile) is caught and translated to `DuplicateLicenseNumberException` (409) — the whole edit (including any other field changes bundled in the same request) rolls back, nothing is partially applied (FR-007, and this feature's own "same transaction" requirement).

The whole method is `@Transactional`, mirroring `DoctorVerificationService.verify`'s existing pattern.

## Request/Response contract

See `contracts/doctor-profile-edit.md` for the exact shapes. Summary: `PATCH /api/v1/admin/doctors/{doctorProfileId}` takes all four editable fields in the request body and returns the updated profile's full summary (same shape as the existing list endpoint's `DoctorProfileSummaryResponse`, from 007), so the caller can see the resulting `licenseVerified` value (whether it reset or not) directly in the response — no separate re-fetch needed.
