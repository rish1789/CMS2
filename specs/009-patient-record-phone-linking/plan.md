# Implementation Plan: Patient Record Auto-Creation & Phone-Based Linking

**Branch**: `009-patient-record-phone-linking` | **Date**: 2026-09-02 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/009-patient-record-phone-linking/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

No prior feature defines a clinic-scoped `Patient` entity at all, so this feature defines it for the first time — minimally, scoped to exactly what its own find-or-create/link logic needs. It ships as a single service method, `PatientLinkingService.findOrCreatePatient(patientAccountId, clinicId, name)`, with no HTTP endpoint (its only two consumers, 017/018, don't exist yet — Constitution Principle III explicitly permits a service-interface-only contract). The method: reuses an already-linked record for that account+clinic if one exists; otherwise links an unlinked walk-in record with a matching phone number (only if it isn't already linked to a *different* account — resolved with the user during Clarify to prevent one account leaking another's history via a shared phone); otherwise creates a new record. Two independent race conditions are closed at the data layer via two separate partial-unique constraints, not one flat constraint.

## Technical Context

**Language/Version**: Java 21 (backend) — this feature is backend-only (no frontend surface; no UI exists to trigger it since its only consumers, 017/018, aren't built yet).

**Primary Dependencies**: Spring Boot 3.x (Data JPA, Transaction) — reuses `com.cms.identity.clinic.Clinic` (001) as a read-only reference and `com.cms.patient.account.PatientAccount`/`PatientAccountRepository` (039) directly. No new external dependency.

**Storage**: PostgreSQL — one new migration (`V5`) creating the `patient` table for the first time, with two partial unique indexes (not one flat constraint — see research.md).

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers (backend only — no frontend tests, since there is no frontend surface).

**Target Platform**: Linux container (Docker).

**Project Type**: Backend service module (no web/API layer in this feature).

**Performance Goals**: Same order of magnitude as prior features (2s p95) — a low-volume, first-booking-only check.

**Constraints**:
- The existing-link check (FR-002), the phone-match-and-link (FR-003), and the create-new path (FR-004) MUST all execute inside one `@Transactional` service method — no partial state on any failure.
- Matching MUST exclude a Patient record already linked to a *different* `PatientAccount` (FR-003) — this is what makes the uniqueness guarantee two separate partial constraints instead of one flat `(clinic, phone)` constraint (research.md).
- A lost race (either constraint) MUST NOT surface as a failure to the caller — it MUST transparently re-read and return the already-committed record (FR-006).
- No new frontend code — there is no UI consumer for this feature yet.

**Scale/Scope**: Single feature — 1 new entity (`Patient`), 1 migration, 1 new repository, 1 new service with one public method, 0 new endpoints.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Test-First Development | PASS | Tests for each of FR-002 through FR-008 (existing-link reuse, phone match, linked-record protection, new-record creation, both race conditions, cross-clinic independence) written before implementation. |
| II. Simplicity & YAGNI | PASS | The `Patient` entity carries only the fields this feature's own logic needs (clinic, phone, name, optional account link, timestamp) — no speculative DOB/address/insurance fields invented ahead of a feature that actually needs them. No premature HTTP endpoint (Assumptions). |
| III. Modular, Library-First Architecture | PASS | New `com.cms.patient.record` package, testable in isolation via its service interface — the explicit "service interface or REST endpoint" contract this principle allows. Reads `Clinic` (identity module) and `PatientAccount` (same patient module) directly, matching this project's existing precedent for clinic-scoped entities referencing the shared `Clinic` entity. |
| IV. Data Privacy & Integrity by Design | PASS | This is the principle's own named example ("patient self-service booking ... MUST close duplicate-creation races at the data layer") implemented directly. The linked-record protection (Clarifications) is itself a privacy-by-design decision: one account can never silently gain access to another's clinical history via a shared phone number. |

No violations — Complexity Tracking is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/009-patient-record-phone-linking/
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
├── src/main/java/com/cms/patient/record/
│   ├── Patient.java                          # new entity
│   ├── PatientRepository.java                # new
│   ├── PatientLinkingService.java             # new — the feature's sole public contract
│   └── PatientAccountNotFoundException.java   # new
├── src/main/resources/db/migration/
│   └── V5__create_patient.sql                # new
└── src/test/java/com/cms/patient/record/
    └── integration/
        ├── PatientLinkingReuseExistingLinkTest.java
        ├── PatientLinkingPhoneMatchTest.java
        ├── PatientLinkingProtectedLinkedRecordTest.java
        ├── PatientLinkingNewRecordTest.java
        ├── PatientLinkingNoPhoneTest.java
        ├── PatientLinkingSameAccountRaceTest.java
        ├── PatientLinkingClinicScopeIndependenceTest.java
        ├── PatientTableUnlinkedPhoneUniquenessTest.java
        ├── PatientLinkingAccountNotFoundTest.java
        └── AbstractPatientRecordIntegrationTest.java
```

**Structure Decision**: A new `com.cms.patient.record` package, sibling to the existing `com.cms.patient.account` and `com.cms.patient.api` — this is genuinely new territory (the clinic-scoped Patient record, as opposed to the global Patient Account), so it gets its own package rather than being folded into `com.cms.patient.account`. No `frontend/` changes at all — there is nothing to build a UI for yet.

## Post-Design Constitution Re-Check

Re-evaluated after Phase 1 (data model + contracts + quickstart, below): all four principles still PASS. The two-partial-constraint design (rather than one flat constraint) is what directly implements the Clarify-stage privacy decision under Principle IV — confirmed no new violations in the detailed design.

## Complexity Tracking

*No violations — table intentionally left empty.*
