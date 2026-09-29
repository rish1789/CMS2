# Implementation Plan: Staff Onboarding (Direct-Hire)

**Branch**: `004-staff-onboarding-direct-hire` | **Date**: 2026-09-02 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-staff-onboarding-direct-hire/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

An authenticated ClinicAdmin submits a single form onboarding a new Doctor or Operations staff member for their own clinic. Account + Role Assignment are created atomically, active immediately, with a system-generated staff code and temporary password returned for hand-off. A Doctor hire additionally gets a Doctor Profile row (`licenseVerified=false`) in the same transaction. Includes a minimal ClinicAdmin email+password login (JWT-issuing), pulled forward from 003 since this feature needs a working caller-authentication mechanism before 003 is built.

## Technical Context

**Language/Version**: Java 21 (backend); TypeScript + React 18 (frontend) — same as prior features.

**Primary Dependencies**: Spring Boot 3.x (Web, Data JPA, Validation, Security) — reuses 001's `PasswordEncoder`, `PasswordPolicyValidator`, `StaffCodeGenerator`; reuses `com.cms.common.IndianMobileNumberValidator` (002); reuses the `jjwt` dependency already added in 002 for JWT issuance.

**Storage**: PostgreSQL — one new table (`doctor_profile`); no changes to 001's `account`/`role_assignment` schema (role enum already includes `Doctor`/`Operations`).

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers (backend); Vitest + React Testing Library (frontend).

**Target Platform**: Linux container (Docker).

**Project Type**: Web application (backend + frontend).

**Performance Goals**: Same order of magnitude as prior features (2s p95).

**Constraints**:
- Onboarding MUST be one atomic transaction (Account + RoleAssignment + optionally DoctorProfile) — full rollback on any failure (FR-009).
- Role MUST be server-side restricted to `Doctor`/`Operations` regardless of client input (FR-003) — enforced by validation, not merely a UI constraint.
- The new staff-login JWT MUST be structurally distinct (different audience claim) from 002's Patient Account JWT — no crossover, mirroring 002's own FR-008 pattern.
- Credentials generated here MUST NOT be emailed/SMS'd (notification delivery is stubbed) — returned directly in the API response for the ClinicAdmin's screen only.

**Scale/Scope**: Single feature — 1 new entity (Doctor Profile), 2 new endpoints (staff login, staff onboarding).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Test-First Development | PASS | Tests for atomicity/rollback, role restriction, password policy, and the new login path written before implementation. |
| II. Simplicity & YAGNI | PASS | The pulled-forward login is the minimum needed to make FR-002 real and testable, not a speculative full auth system — 003 extends it, doesn't replace it. No invitation/accept-link scaffolding (explicitly excluded). |
| III. Modular, Library-First Architecture | PASS | All new code stays within `com.cms.identity.*`, reusing 001's existing repositories/validators/generators directly rather than duplicating them (unlike 002, which had genuine reasons to keep some logic separate). |
| IV. Data Privacy & Integrity by Design | PASS | Temporary password hashed before storage, generated staff code globally unique (reuses 001's `StaffCodeGenerator`, closing the same race at the DB layer). New staff-login JWT credential handling follows the same never-log/never-expose discipline as 001/002. |

No violations — Complexity Tracking is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/004-staff-onboarding-direct-hire/
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
├── src/main/java/com/cms/identity/account/
│   ├── StaffJwtService.java            # issues/validates STAFF-audience tokens
│   ├── StaffAuthController.java        # POST /api/v1/staff/login
│   ├── StaffSecurityConfig.java        # @Order(4): permits /api/v1/staff/login, JWT-protects the rest of /api/v1/clinics/**/staff/**
│   └── TemporaryPasswordGenerator.java # generates a policy-compliant random password by construction
├── src/main/java/com/cms/identity/doctor/
│   ├── DoctorProfile.java
│   └── DoctorProfileRepository.java
├── src/main/java/com/cms/identity/staff/
│   ├── StaffOnboardingService.java
│   ├── StaffOnboardingController.java  # POST /api/v1/clinics/{clinicId}/staff
│   └── dto/
├── src/main/resources/db/migration/
│   └── V3__create_doctor_profile.sql
└── src/test/java/com/cms/identity/
    ├── account/                         # StaffJwtService tests
    └── staff/integration/

frontend/
├── src/features/staff-login/
│   ├── StaffLoginForm.tsx
│   └── api.ts
├── src/features/staff-onboarding/
│   ├── OnboardStaffForm.tsx
│   └── api.ts
└── tests/
    ├── staff-login/
    └── staff-onboarding/
```

**Structure Decision**: Extends `com.cms.identity` with three new sub-packages (`account` gets the new login pieces, `doctor` and `staff` are new) rather than a new top-level module — this is all staff-identity territory (research.md).

## Post-Design Constitution Re-Check

Re-evaluated after Phase 1 (data model + contracts + quickstart, below): all four principles still PASS. The transactional atomicity design and the `STAFF`-vs-`PATIENT` JWT audience separation directly implement Principle IV and the no-crossover constraint — no new violations.

## Complexity Tracking

*No violations — table intentionally left empty.*
