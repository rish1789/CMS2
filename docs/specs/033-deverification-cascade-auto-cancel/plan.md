# Implementation Plan: De-Verification Cascade (Auto-Cancel Future Bookings)

**Branch**: `033-deverification-cascade-auto-cancel` | **Date**: 2026-09-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/033-deverification-cascade-auto-cancel/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Close the "de-verification has no cascade" gap: when a Clinic is un-verified or a Doctor's license
is explicitly revoked, automatically cancel every still-pending booking tied to it. Technical
approach: finally consume `com.cms.identity.admin.ClinicDeVerifiedEvent` (003), which has existed
with no listener since that feature shipped; add the missing explicit doctor-license-revoke admin
action (the necessary prerequisite Trigger 2 needs) publishing an analogous new event; and a
shared `DeVerificationCascadeService` that batch-cancels the matching bookings — reusing
`BookingCancellationService` (025) directly for fixed-time bookings (the real waitlist-bump
trigger) and a direct `cancelIfActive`-based path for Queue-mode ones, mirroring 029/030's own
established non-025 bulk-cancellation shape.

## Technical Context

**Language/Version**: Java 21 (backend) — this feature is backend-only, no frontend surface (a Super Admin action with no existing UI in this codebase).

**Primary Dependencies**: Spring Boot, Spring Data JPA, Spring's event-listener support (`@TransactionalEventListener`, already established by 028/037's identical pattern).

**Storage**: PostgreSQL — no new migration; every entity involved already exists.

**Testing**: JUnit 5 + Spring Boot Test (`@WebMvcTest`/`@SpringBootTest` + Testcontainers) — mirrors every prior feature.

**Target Platform**: Linux server (Spring Boot backend).

**Project Type**: Web application (backend-only for this feature; `backend/` of the existing `backend/` + `frontend/` structure).

**Performance Goals**: No new performance target — a cascade runs once per de-verification action over a small per-clinic/per-doctor candidate set, mirroring 026/027's own untimed bulk-cancellation scope.

**Constraints**: The automatic, edit-triggered `licenseVerified` reset (006) MUST NEVER trigger this cascade — only the new explicit revoke action may (FR-002/FR-009).

**Scale/Scope**: One new admin action (doctor license revoke), one new domain event, two new `@TransactionalEventListener` methods, one new cascade service, no new module, no new entity, no new migration.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Test-First Development**: PASS. Tasks will include failing tests (unit + Testcontainers
  integration) for: clinic un-verify cancels every future booking across doctors; a cancelled
  fixed-time booking triggers a real waitlist bump; a cancelled Queue-mode booking releases its
  Slot with no waitlist bump; past/completed bookings are left untouched; the explicit doctor
  revoke action cancels that doctor's future bookings across clinics; 006's automatic reset never
  triggers the cascade; idempotent re-un-verify/re-revoke never double-cancels.
- **II. Simplicity & YAGNI**: PASS. No new module; reuses `BookingCancellationService` for
  fixed-time bookings rather than reimplementing cancellation; the Queue-mode path mirrors
  029/030's already-proven direct-`cancelIfActive` shape rather than inventing a third pattern; no
  request body/reason field on the revoke action since none is stated as required (spec
  Assumptions); no restore-on-re-verify mechanism (explicitly out of scope).
- **III. Modular, Library-First Architecture**: PASS. `com.cms.identity.admin` gains the new
  revoke action and event (alongside its existing `ClinicVerificationService`/
  `ClinicDeVerifiedEvent`); the cascade itself lives in `com.cms.booking` (it fundamentally
  operates on Bookings, the same placement precedent as `BookingCancellationService`/
  `SessionCancellationService`), listening to both admin-module events entirely through Spring's
  event mechanism — no reach-through into `com.cms.identity.admin`'s internals beyond the two
  published event types.
- **IV. Data Privacy & Integrity by Design**: PASS. Cancellation itself is already
  data-layer-guarded (`cancelIfActive`, 025) — this feature is a new *caller* of that existing
  guarantee, not a new concurrency-sensitive write of its own. No clinical documentation is
  touched (FR-007). Multi-tenancy: the clinic-triggered cascade is naturally clinic-scoped by its
  own query; the doctor-triggered cascade intentionally spans every clinic that doctor is staffed
  at, since `DoctorProfile` is itself a global (not per-clinic) entity — the constitution's own
  stated exception.

No violations — Complexity Tracking table not needed.

**Post-Phase-1 re-check**: PASS, unchanged. Phase 1 design (data-model.md, contracts/,
quickstart.md) introduced nothing beyond what this gate already evaluated.

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/cms/identity/admin/
│   ├── DoctorVerificationService.java              # extend: + revoke()
│   ├── DoctorVerificationController.java           # extend: + POST /{doctorProfileId}/revoke
│   └── DoctorLicenseRevokedEvent.java              # new, mirrors ClinicDeVerifiedEvent exactly
├── src/main/java/com/cms/booking/
│   ├── BookingRepository.java                      # extend: + findActiveFutureBookingsByClinic/ByDoctor
│   ├── DeVerificationCascadeService.java            # new: shared cascade core (both triggers)
│   └── DeVerificationCascadeListener.java           # new: 2 @TransactionalEventListener methods
└── src/test/java/com/cms/booking/integration/       # extend existing package
    └── (new test classes)
```

**Structure Decision**: No new module. The new admin action + event live in the existing
`com.cms.identity.admin` package (alongside `ClinicVerificationService`/`ClinicDeVerifiedEvent`,
which this feature directly parallels); the cascade service + listener live in `com.cms.booking`
(the module that already owns every other cancellation service - `BookingCancellationService`,
`SessionCancellationService`, `SessionPartialCancellationService`). No new `SecurityConfig` — the
new revoke endpoint falls under the existing blanket `/api/v1/admin/**` authenticated chain
(003), which needs no per-path matchers at all (unlike the staff/patient chains).

## Complexity Tracking

*No violations — this section is not applicable.*
