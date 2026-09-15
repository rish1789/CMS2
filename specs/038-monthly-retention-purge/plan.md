# Implementation Plan: Monthly Automatic Retention Purge

**Branch**: `038-monthly-retention-purge` | **Date**: 2026-09-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/038-monthly-retention-purge/spec.md`

## Summary

Add an automatic monthly background job that permanently deletes clinical-record content (Consultation Notes, Prescriptions/Items, External Record References) for bookings that are both 3+ years old (by `Booking.createdAt`) and belong to an already-anonymized patient (feature 037). The booking record and the anonymized `Patient` shell are never touched. A Super Admin-only manual-trigger endpoint runs the same logic on demand. The purge service lives in `com.cms.clinical` (the module owning the content being deleted); the automatic trigger is a `@Scheduled` component alongside it; the manual-trigger controller lives in `com.cms.identity.admin` under the existing Super Admin HTTP Basic Auth chain.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot (Web, Data JPA, Security, Scheduling), Flyway

**Storage**: PostgreSQL

**Testing**: JUnit 5 + Spring Boot Test (MockMvc integration tests), AssertJ

**Target Platform**: Linux server (Spring Boot backend)

**Project Type**: web (backend + frontend); this feature is backend-only (no UI — purge is a background job plus a Super Admin operational endpoint, not a staff/patient-facing feature)

**Performance Goals**: N/A — a low-frequency monthly batch job; no throughput target

**Constraints**: Purge must be idempotent (safe to re-run); must never delete the booking row or the Patient row; must not touch non-anonymized patients' content regardless of age

**Scale/Scope**: Clinic-management scale (not web-scale) — a straightforward query-then-loop sweep, mirroring `NoShowDetectionService`/`WaitlistExpirySweepService`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Test-First Development**: PASS — tests will be written before implementation for the purge service (all four condition-combinations from spec.md's acceptance scenarios) and for the manual-trigger endpoint's authorization (Super Admin allowed, all other roles rejected), following Red-Green-Refactor.
- **II. Simplicity & YAGNI**: PASS — reuses `JpaRepository`'s inherited `deleteById`/`delete` (no new repository methods needed for deletion itself), a single query-then-loop sweep (no batching/pagination infrastructure, matching existing sweep services' scale), and the 3-year window is a hardcoded constant (no configurability, per spec FR-006 and constitution's own YAGNI example).
- **III. Modular, Library-First Architecture**: PASS — purge logic lives in `com.cms.clinical` (the module owning the entities being deleted), reading `com.cms.booking.Booking` and `com.cms.patient.record.Patient` directly for its own precondition checks — consistent with `com.cms.clinical`'s existing services already reading `com.cms.booking.Booking` directly (030/031/032), and with the existing dependency direction `clinical → booking → patient.record` (no reversal). No new event needed (no cross-module side effect beyond the deletions themselves).
- **IV. Data Privacy & Integrity by Design**: PASS — this feature *is* the constitution's own named "monthly-purge" half of the anonymization/retention lifecycle (see Principle IV's own text). It does not weaken clinical-documentation immutability's *edit* prohibition (no update path is added); permanent deletion under the documented retention/anonymization precondition is the explicitly anticipated exception, not a workaround.

No violations — Complexity Tracking not needed.

## Project Structure

### Documentation (this feature)

```text
specs/038-monthly-retention-purge/
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
├── src/main/java/com/cms/
│   ├── clinical/
│   │   ├── RetentionPurgeService.java        # NEW - purge logic, precondition checks, deletion
│   │   ├── RetentionPurgeTrigger.java        # NEW - @Scheduled monthly trigger (mirrors WaitlistExpirySweepTrigger)
│   │   ├── ConsultationNoteRepository.java   # existing (034/030) - deleteById used, no new methods
│   │   ├── PrescriptionRepository.java       # existing (035/031) - deleteById used, no new methods
│   │   ├── Prescription.java                 # MODIFIED - cascade extended to include REMOVE
│   │   └── ExternalRecordReferenceRepository.java  # existing (036/032) - deleteById used, no new methods
│   ├── booking/
│   │   └── BookingRepository.java            # MODIFIED - add findRetentionEligibleBookings query
│   └── identity/admin/
│       ├── RetentionPurgeController.java     # NEW - POST /api/v1/admin/retention-purge/run, Super Admin only
│       └── dto/
│           └── RetentionPurgeResultResponse.java  # NEW
├── src/main/resources/db/migration/
│   └── (none needed - reuses existing schema, no new columns/tables)
└── src/test/java/com/cms/
    ├── clinical/
    │   └── integration/
    │       ├── RetentionPurgeTest.java              # NEW
    │       └── AbstractRetentionPurgeIntegrationTest.java  # NEW fixture
    └── identity/admin/
        └── integration/
            └── RetentionPurgeAuthorizationTest.java  # NEW
```

**Structure Decision**: Backend-only, web application structure (existing `backend/` module). The purge service and its automatic trigger live in `com.cms.clinical` (owns the deleted content, already reads `Booking` directly per 030/031/032 precedent). The Super Admin manual-trigger controller lives in `com.cms.identity.admin`, alongside `ClinicVerificationController`/`DoctorVerificationController`, sitting behind the existing `SuperAdminSecurityConfig` HTTP Basic Auth chain (`/api/v1/admin/**`) — this is the correct placement here (unlike feature 037's `PatientAnonymizationController`, which does NOT belong in `com.cms.identity.admin` because its own gate is staff-JWT, not Super-Admin-Basic-Auth; this feature's manual trigger genuinely IS Super-Admin-gated per its own business rule).
