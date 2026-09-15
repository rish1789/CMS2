# Research: Doctor Profile Auto-Creation & License Verification Queue

No `NEEDS CLARIFICATION` markers remain in Technical Context — every open question was resolved either during spec Clarifications (with the user) or by inspecting the existing 003/004 implementation this feature extends. This document records the resulting technical decisions.

## Decision: Verification-queue implementation mirrors 003's clinic-verification pattern exactly

**Rationale**: 003-super-admin-verification already solved the identical shape of problem (Super Admin lists pending items, flips a boolean, idempotently, HTTP Basic Auth, no new security chain) for `Clinic.verified`. `SuperAdminSecurityConfig`'s matcher is already `/api/v1/admin/**`, so a new `/api/v1/admin/doctors/**` controller needs zero security-config changes. `ClinicVerificationController`/`ClinicVerificationService`/`AdminExceptionHandler` are the direct templates for `DoctorVerificationController`/`DoctorVerificationService`.

**Alternatives considered**: A generic "verifiable entity" abstraction shared between Clinic and Doctor Profile — rejected per Constitution Principle II (YAGNI): two concrete implementations of an identical small pattern is simpler and more legible than an abstraction built for a set of exactly two members, especially since Clinic's `verify`/`unverify` is a genuine two-way toggle (008's de-verification cascade) while Doctor Profile's is currently one-way in this feature's scope (no un-verify requirement in the spec).

## Decision: Onboarding-time dedup matches on license number, cross-checked against specialization (case-insensitive, trimmed)

**Rationale**: Resolved directly with the user during spec Clarifications. License number is the only field on 004's existing `OnboardStaffRequest.DoctorDto` that is (a) mandatory and (b) inherently a real-world unique-per-doctor identifier — no new form field or search UI is needed. Specialization is free text (`DoctorDto.specialization` — plain `String`, only validated non-blank in `StaffOnboardingService.requireDoctorFields`), so an exact-literal comparison would create false conflicts from formatting alone; case-insensitive + trimmed avoids that while still catching genuine specialty mismatches (e.g. "ENT" submitted against a profile on file as "Radiology").

**Alternatives considered**: Mobile number as the match key — rejected, it's optional on the onboarding form (004 FR-008) and can't reliably identify every submission. A dedicated "search for existing doctor" UI step — rejected as unnecessary scope expansion; the license-number lookup happens transparently on submit.

## Decision: `uq_doctor_profile_license_number` enforced at the database layer, not just app-level pre-check

**Rationale**: Constitution Principle IV requires concurrency-sensitive identity-matching operations to close duplicate-creation races at the data layer. `StaffOnboardingService` already follows exactly this pattern for `Account.email` (`existsByEmail` pre-check + catching the `uq_account_email` violation as the actual source of correctness — see the class's existing doc comment). The license-number dedup gets the identical treatment: a fast pre-check via `DoctorProfileRepository.findByLicenseNumber`, backed by a DB unique constraint that is what actually prevents two concurrent onboarding submissions for the same license number from both succeeding.

**Alternatives considered**: Optimistic locking / `SELECT ... FOR UPDATE` on the lookup — rejected as unnecessary complexity; the existing project-wide pattern (pre-check + DB constraint + catch-and-translate) already closes the race without pessimistic locking, and is what every other identity-uniqueness invariant in this codebase already uses.

## Decision: `licenseVerified` carry-over on reuse requires no new code — it's the absence of a reset

**Rationale**: The reuse branch in `StaffOnboardingService.onboard` never touches the existing `DoctorProfile` row's `licenseVerified` field at all — it only creates a new `RoleAssignment`. "Carries over unchanged" (spec Clarifications) is therefore the natural behavior of *not* writing to that field on this path, not a feature that needs to be built. The risk this decision guards against is implementing the reuse branch by re-constructing/re-saving a `DoctorProfile` (which could accidentally reset `licenseVerified` to its default) instead of loading and reusing the existing row as-is.

## Decision: No new credentials issued on reuse — signaled via a new `existingAccount` boolean on `OnboardStaffResponse`

**Rationale**: `OnboardStaffResponse` already carries `temporaryPassword`; on the reuse branch this becomes `null` (no password was generated), and a new `existingAccount: boolean` field lets the frontend distinguish "here are new credentials" from "this doctor already has an account, no new credentials" without the client having to infer it from `temporaryPassword == null` alone (which would be a fragile implicit contract). `staffCode` is still populated on both branches — the existing doctor's current staff code on reuse, a freshly generated one otherwise — so the ClinicAdmin always has something to reference.

**Alternatives considered**: A separate endpoint/response shape for the reuse case — rejected; it's the same onboarding action from the ClinicAdmin's perspective (one form, one submit), and 004's existing single-response contract only needs one additive field, not a second contract.

## Decision: Frontend doctor-verification screen mirrors `clinic-verification/` structurally, as a new sibling feature folder

**Rationale**: `frontend/src/features/clinic-verification/{PendingClinicsList.tsx,api.ts}` is a complete, working reference implementation of "Super Admin logs in with Basic Auth (stored in `sessionStorage`), tabs between pending/verified, lists items, acts on one." `doctor-verification/` reuses the same shape (tabs, list, action button, 401 handling, credential storage) against `/api/v1/admin/doctors` instead of `/api/v1/admin/clinics`. No shared component is extracted for two instances (YAGNI) — if a third Super Admin worklist is added later, that's the point to reconsider extraction, not before.

**Alternatives considered**: Extracting a shared `<AdminVerificationList>` component now — rejected per Principle II; two call sites is not yet a pattern that justifies the abstraction's indirection cost, and the two lists' summary fields (clinic address vs. doctor specialization/license/experience) differ enough that a generic component would need render-prop or slot complexity disproportionate to the size of the duplication it would remove.
