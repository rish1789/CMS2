# Implementation Plan: Doctor License Edit Triggers Re-Verification Reset

**Branch**: `008-doctor-license-reverification-reset` | **Date**: 2026-09-02 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/008-doctor-license-reverification-reset/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

No prior feature builds any Doctor Profile edit capability at all, so this feature builds it — scoped to Super Admin only (resolved with the user during Specify), alongside 005/007's existing pending-verification worklist under `/api/v1/admin/doctors`. The edit action covers all four editable `DoctorProfile` fields (specialization, license number, experience years, and 007's previously-unwritable `visible` toggle — also resolved with the user to close that gap here). Its one precise side effect: if the submitted license number differs from the currently-stored value and `licenseVerified` is currently `true`, `licenseVerified` resets to `false` in the same transaction as the edit — and nothing else (no cascade to bookings, that's 008-deverification-cascade-auto-cancel-bookings' separate mechanism).

## Technical Context

**Language/Version**: Java 21 (backend); TypeScript + React 18 (frontend) — same as prior features.

**Primary Dependencies**: Spring Boot 3.x (Web, Data JPA, Validation, Security) — reuses 005/007's `DoctorProfile`/`DoctorProfileRepository`/`DoctorVerificationController`/`DoctorVerificationService`/`SuperAdminSecurityConfig` directly; no new dependency.

**Storage**: PostgreSQL — **no migration needed**. Every field this feature edits (`specialization`, `license_number`, `experience_years`, `license_verified`, `visible`) and the uniqueness constraint it relies on (`uq_doctor_profile_license_number`) already exist as of 007's `V4` migration.

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers (backend); Vitest + React Testing Library (frontend).

**Target Platform**: Linux container (Docker).

**Project Type**: Web application (backend + frontend).

**Performance Goals**: Same order of magnitude as prior features (2s p95) — a low-volume Super Admin admin action.

**Constraints**:
- The license-number-changed comparison, the field update, and the conditional `licenseVerified` reset MUST all happen inside one `@Transactional` method — no partial state (FR-003's "same transaction" requirement).
- The reset MUST NOT call into, publish an event toward, or otherwise trigger 008-deverification-cascade-auto-cancel-bookings' cascade (FR-006) — unlike 003's `unverify`, which does publish `ClinicDeVerifiedEvent`, this reset publishes nothing.
- The edited license number MUST be checked against `uq_doctor_profile_license_number` (already enforced at the DB layer since 007) — an edit that would collide with a different Doctor Profile's license number is rejected, not silently merged.
- The edit endpoint sits behind the existing `SuperAdminSecurityConfig` `/api/v1/admin/**` matcher — no new security-config code.

**Scale/Scope**: Single feature — 0 new entities, 0 migrations, 1 new endpoint (edit), 3 new entity setters (`specialization`, `licenseNumber`, `experienceYears` — `licenseVerified`/`visible` setters already exist from 007).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Test-First Development | PASS | Tests for the reset-on-license-change rule, the no-reset-on-other-fields rule, the no-op-same-value case, the duplicate-license rejection, the visibility-toggle write, and Super Admin-only authorization, all written before implementation. |
| II. Simplicity & YAGNI | PASS | No new module, no new security chain, no migration. Reuses 005/007's `DoctorVerificationController`/`Service` as the single home for all Doctor Profile administration actions, rather than fragmenting into a separate edit-specific controller. |
| III. Modular, Library-First Architecture | PASS | Stays entirely within `com.cms.identity.admin` (the action) and `com.cms.identity.doctor` (the entity setters) — no cross-module reach-through. The reset logic is a plain conditional inside the edit transaction, not an event — deliberately *not* using the event-driven pattern 003 uses for its cascade, since FR-006 requires these two mechanisms to stay fully independent. |
| IV. Data Privacy & Integrity by Design | PASS | Reuses 007's existing DB-level `uq_doctor_profile_license_number` constraint as the source of correctness for the duplicate-license case (Constitution Principle IV) rather than relying on an app-level check alone. |

No violations — Complexity Tracking is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/008-doctor-license-reverification-reset/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
└── tasks.md
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/cms/identity/doctor/
│   └── DoctorProfile.java                       # existing file: + setSpecialization, setLicenseNumber, setExperienceYears
├── src/main/java/com/cms/identity/admin/
│   ├── DoctorVerificationService.java            # existing file: + edit(UUID, EditDoctorProfileCommand) with the reset rule
│   ├── DoctorVerificationController.java         # existing file: + PATCH /api/v1/admin/doctors/{doctorProfileId}
│   ├── DuplicateLicenseNumberException.java      # new
│   ├── AdminExceptionHandler.java                # existing file: + handler for DuplicateLicenseNumberException
│   └── dto/
│       └── EditDoctorProfileRequest.java         # new
└── src/test/java/com/cms/identity/admin/integration/
    └── (new test classes alongside existing clinic/doctor-verification tests)

frontend/
├── src/features/doctor-verification/
│   ├── PendingDoctorsList.tsx                    # existing file: + inline "Edit" action per row
│   └── api.ts                                    # existing file: + editDoctor client function
└── tests/doctor-verification/
    └── PendingDoctorsList.test.tsx                # existing file: + edit-flow test cases
```

**Structure Decision**: No new backend module and no new frontend feature-area — everything extends files 005/007 already created. The edit action lives on the same `DoctorVerificationController`/`Service` as the verify action (both are "Super Admin administers a Doctor Profile" operations on the same resource), matching how 003's `ClinicVerificationService` already hosts both `verify` and `unverify` together.

## Post-Design Constitution Re-Check

Re-evaluated after Phase 1 (data model + contracts + quickstart, below): all four principles still PASS. Keeping the reset a plain in-transaction conditional (not an event) is what directly implements FR-006's independence requirement from 008's cascade — confirmed no new violations in the detailed design.

## Complexity Tracking

*No violations — table intentionally left empty.*
