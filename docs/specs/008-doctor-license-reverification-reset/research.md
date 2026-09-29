# Research: Doctor License Edit Triggers Re-Verification Reset

No `NEEDS CLARIFICATION` markers remain in Technical Context. Both open product questions (who edits, does editing cover `visible`) were resolved with the user during Specify/Clarify. This document records the resulting technical decisions.

## Decision: The edit action lives on the existing `DoctorVerificationController`/`Service`, not a new controller

**Rationale**: 005/007 already established `com.cms.identity.admin.DoctorVerificationController`/`Service` as "Super Admin administers a Doctor Profile" for the verify action. This feature adds a second action (edit) on the *same resource* under the *same actor* — exactly the shape 003's `ClinicVerificationService` already has with its `verify`/`unverify` pair. Splitting edit into a separate controller/service would duplicate the `SuperAdminSecurityConfig` wiring notes and fragment one resource's administration across two files for no benefit.

**Alternatives considered**: A new `DoctorProfileAdminController` dedicated to editing — rejected per Constitution Principle II (YAGNI); no distinct concern justifies the split.

## Decision: A single, full-payload PATCH endpoint (`specialization`, `licenseNumber`, `experienceYears`, `visible` all required in the request), not partial/optional-field updates

**Rationale**: Every acceptance scenario in the spec describes "the edit" as one holistic save (a form submission), never a partial patch of just one field via separate calls. Requiring all four fields on every request avoids the null-vs-absent ambiguity a partial-update DTO would introduce (does a missing field mean "don't change it" or "clear it"?) — the same simplicity trade-off 004's onboarding DTO already makes (its `doctor` sub-object is all-or-nothing, not independently-patchable fields).

**Alternatives considered**: JSON Merge Patch semantics (only present fields change) — rejected as unnecessary complexity for a low-volume admin form where the frontend always has the full current record loaded before editing.

## Decision: The reset is a plain in-transaction conditional, not an event

**Rationale**: FR-006 requires this reset to stay fully independent from 008-deverification-cascade-auto-cancel-bookings' cascade. 003's `unverify` publishes `ClinicDeVerifiedEvent` specifically *because* 008 needs to listen for it and cascade. This feature's reset must NOT be observable the same way — publishing an event here would create exactly the coupling risk FR-006 forbids (a future listener could accidentally attach cascade behavior to it). A plain `if (licenseNumberChanged && wasVerified) { profile.setLicenseVerified(false); }` inside `edit()`'s existing transaction has no event to subscribe to, structurally enforcing the independence the spec requires.

**Alternatives considered**: Reusing the `ApplicationEvent` pattern from 003 for consistency — rejected; consistency with a pattern is not a reason to introduce the exact coupling risk this feature's spec explicitly rules out.

## Decision: No new migration — all fields and constraints already exist from 007

**Rationale**: `DoctorProfile.specialization`/`licenseNumber`/`experienceYears`/`licenseVerified`/`visible` and the `uq_doctor_profile_license_number` DB constraint were all added by 004/007. Editing is pure behavior on top of existing schema. `DoctorProfile` gains three new setters (`setSpecialization`, `setLicenseNumber`, `setExperienceYears`) to support the edit path — `setLicenseVerified`/`setVisible` already exist from 007.

**Alternatives considered**: None — this is a straightforward observation, not a decision with real alternatives.

## Decision: Duplicate-license-on-edit reuses the same DB-constraint-catch pattern as onboarding's dedup (007), via a new `DuplicateLicenseNumberException`

**Rationale**: FR-007 requires the same global-uniqueness guarantee onboarding already has. 007's `StaffOnboardingService` established the project's pattern for this: attempt the write, catch the `uq_doctor_profile_license_number` violation, translate to a domain exception. The edit path reuses that exact pattern rather than doing a separate pre-check-only implementation (which would reintroduce the TOCTOU race 007 specifically closed at the DB layer).

**Alternatives considered**: A pre-check-only implementation (`existsByLicenseNumber` before saving, no catch) — rejected; would reopen the same concurrent-edit race Constitution Principle IV requires closing at the data layer, for two different Super Admin sessions editing two different profiles to the same new license number concurrently.
