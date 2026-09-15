# Implementation Plan: Doctor Profile Auto-Creation & License Verification Queue

**Branch**: `007-doctor-profile-license-queue` | **Date**: 2026-09-02 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/007-doctor-profile-license-queue/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Doctor Profile creation already exists as a side effect of 004's onboarding transaction (`licenseVerified` starts `false`). This feature adds: (1) a Super Admin worklist + verify action for the license-verification queue, mirroring 003's clinic-verification pattern exactly; (2) a public-visibility toggle field, defaulting `true`; (3) onboarding-time dedup so a doctor onboarded at a second clinic reuses their existing global Account + Doctor Profile instead of creating a duplicate — matched by license number, cross-checked against specialization (case-insensitive, trimmed), with `licenseVerified` carried over unchanged and no new credentials issued on reuse.

## Technical Context

**Language/Version**: Java 21 (backend); TypeScript + React 18 (frontend) — same as prior features.

**Primary Dependencies**: Spring Boot 3.x (Web, Data JPA, Validation, Security) — reuses 004's `DoctorProfile`/`DoctorProfileRepository`/`StaffOnboardingService`, 003's `SuperAdminSecurityConfig` (HTTP Basic Auth, already scoped to `/api/v1/admin/**`) and its `ClinicVerificationController`/`Service` pattern verbatim for the doctor-verification analog.

**Storage**: PostgreSQL — one migration (`V4`) altering the existing `doctor_profile` table: adds `visible BOOLEAN NOT NULL DEFAULT TRUE`, adds `UNIQUE (license_number)` (closes the onboarding-time dedup race at the DB layer, per Constitution Principle IV — mirrors how `uq_account_email` already backstops 004's app-level email check).

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers (backend); Vitest + React Testing Library (frontend).

**Target Platform**: Linux container (Docker).

**Project Type**: Web application (backend + frontend).

**Performance Goals**: Same order of magnitude as prior features (2s p95) — this is a low-volume admin worklist and an onboarding-time lookup, not a hot path.

**Constraints**:
- The license-number lookup + specialization check + (Account reuse XOR new-Account creation) + RoleAssignment/DoctorProfile write MUST remain inside `StaffOnboardingService.onboard`'s existing `@Transactional` boundary — no partial state on any failure (extends 004's FR-009 guarantee to the reuse path).
- `DoctorProfile.licenseVerified` MUST NOT be reset by the reuse path (FR-002c) — only 006 (license edit) resets it; this feature must not touch that field on the reuse branch at all.
- The verify action (FR-004..FR-007) MUST reuse `SuperAdminSecurityConfig`'s existing `/api/v1/admin/**` matcher — no new security filter chain.
- No new credentials (staff code/password) are generated on the reuse branch (FR-002b) — `OnboardStaffResponse` needs a way to signal "existing account, no new password" to the caller without breaking 004's existing shape for the non-reuse branch.

**Scale/Scope**: Single feature — 0 new entities (extends existing `DoctorProfile`), 1 migration, 2 new endpoints (list pending doctors, verify), 1 extended endpoint (staff onboarding's Doctor path).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Test-First Development | PASS | Tests for the dedup/reuse branch (match, specialization-mismatch reject, no-new-credentials, licenseVerified carry-over), the verify action's idempotency/authorization, and the discovery-eligibility AND-condition, all written before implementation. |
| II. Simplicity & YAGNI | PASS | No new module, no new security chain, no new UI framework piece — directly reuses 003's verification-screen pattern and 004's onboarding transaction. The visibility toggle's read/write UX beyond the default is explicitly deferred (spec Assumptions), not spuriously built now. |
| III. Modular, Library-First Architecture | PASS | Verification workflow stays in `com.cms.identity.admin` (alongside its clinic-verification sibling); the reuse/dedup logic stays inside `com.cms.identity.staff.StaffOnboardingService`, which already owns Doctor Profile creation — no new cross-module reach-through. |
| IV. Data Privacy & Integrity by Design | PASS | The onboarding-time dedup race (two concurrent submissions for the same license number) is closed at the DB layer via `uq_doctor_profile_license_number`, not merely the app-level pre-check — directly the pattern this principle requires for concurrency-sensitive identity-matching operations. |

No violations — Complexity Tracking is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/007-doctor-profile-license-queue/
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
│   ├── DoctorProfile.java                    # + visible field, setVisible, setLicenseVerified (existing file, extended)
│   └── DoctorProfileRepository.java          # + findByLicenseNumber, findByLicenseVerified (existing file, extended)
├── src/main/java/com/cms/identity/staff/
│   ├── StaffOnboardingService.java           # existing file: Doctor path gets license-number lookup + reuse/reject branch
│   ├── SpecializationMismatchException.java  # new
│   ├── StaffExceptionHandler.java            # existing file: + handler for SpecializationMismatchException
│   └── dto/
│       └── OnboardStaffResponse.java         # existing file: + existingAccount boolean
├── src/main/java/com/cms/identity/admin/
│   ├── DoctorVerificationController.java     # new — GET/POST under /api/v1/admin/doctors, mirrors ClinicVerificationController
│   ├── DoctorVerificationService.java        # new — mirrors ClinicVerificationService (list/verify, idempotent)
│   ├── DoctorProfileNotFoundException.java   # new
│   ├── AdminExceptionHandler.java            # existing file: + handler for DoctorProfileNotFoundException
│   └── dto/
│       ├── DoctorProfileListResponse.java    # new
│       ├── DoctorProfileSummaryResponse.java # new
│       └── DoctorVerificationStatusResponse.java # new
├── src/main/resources/db/migration/
│   └── V4__doctor_profile_visibility_and_license_uniqueness.sql  # new
└── src/test/java/com/cms/identity/
    ├── staff/                                 # onboarding reuse/dedup tests (unit + integration)
    └── admin/                                 # doctor-verification tests (unit + integration), alongside existing clinic-verification tests

frontend/
├── src/features/doctor-verification/
│   ├── PendingDoctorsList.tsx                # new — mirrors clinic-verification/PendingClinicsList.tsx structurally
│   └── api.ts                                # new — mirrors clinic-verification/api.ts
├── src/features/staff-onboarding/
│   ├── OnboardStaffForm.tsx                  # existing file: surfaces "doctor already has an account" outcome
│   └── api.ts                                # existing file: + existingAccount field on the response type
└── tests/
    └── doctor-verification/
        └── PendingDoctorsList.test.tsx        # new
```

**Structure Decision**: No new backend module or frontend feature-area beyond `doctor-verification` (which mirrors `clinic-verification` 1:1, per research.md) — everything else extends files that already exist from 003/004. This keeps the module boundary exactly where 004 already drew it (Doctor Profile lifecycle lives in `identity.doctor` + `identity.staff`; the Super Admin verification workflow lives in `identity.admin`, alongside its clinic-verification sibling).

## Post-Design Constitution Re-Check

Re-evaluated after Phase 1 (data model + contracts + quickstart, below): all four principles still PASS. The DB-level `uq_doctor_profile_license_number` constraint (Principle IV) and the direct reuse of 003's verification pattern / 004's onboarding transaction boundary (Principles II/III) hold through the detailed design — no new violations surfaced.

## Complexity Tracking

*No violations — table intentionally left empty.*
