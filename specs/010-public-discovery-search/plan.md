# Implementation Plan: Public Discovery Search

**Branch**: `010-public-discovery-search` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/010-public-discovery-search/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

A single public, unauthenticated GET endpoint (`/api/v1/discovery/search`) that returns doctor-centric result rows — one per (eligible doctor, eligible clinic assignment) pair — filtered by an optional free-text `q` parameter matching specialization, doctor name, clinic name, or clinic address. Doctor-side eligibility is entirely delegated to 007's already-tested `DoctorProfileRepository.findDiscoveryEligible()`; this feature adds one new repository query that joins that same eligible-doctor set against `RoleAssignment` (to resolve *which* verified clinic) and applies the optional text filter, all inside the query itself (Constitution Principle IV — data-layer enforcement, not response filtering). No new entity, no new migration — purely a new read path over existing tables. Ships with a matching frontend search page (`frontend/src/features/discovery/`), since this is a directly patient-facing feature and every other patient-facing capability in this codebase (signup, login) already ships its own UI.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript/React (frontend) — this feature has both, since it's a first-class public UI surface, matching the precedent of `patient-account` (signup/login) rather than the service-only precedent of `009-patient-record-phone-linking`.

**Primary Dependencies**: Spring Boot 3.x (Data JPA, Web) — reuses `com.cms.identity.clinic.Clinic` (001), `com.cms.identity.doctor.DoctorProfile`/`DoctorProfileRepository` (005/007), `com.cms.identity.account.RoleAssignment` (004) directly, all read-only. Frontend reuses the existing React + Tailwind stack and fetch pattern already established by `patient-account`'s forms. No new external dependency either side.

**Storage**: PostgreSQL — no new migration. This feature only reads existing `clinic`, `doctor_profile`, `account`, and `role_assignment` tables via a new JPQL query.

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers (backend, repository/controller integration tests); Vitest/React Testing Library pattern already used under `frontend/src/features/*` (frontend).

**Target Platform**: Linux container (Docker) for the backend; static SPA build for the frontend — both unchanged from prior features.

**Project Type**: Web application (backend + frontend), following the same split as `clinic-registration`/`patient-account`.

**Performance Goals**: Same order of magnitude as prior features (2s p95) — a single indexed-join read query over v1-scale data; no pagination is introduced (spec Assumptions — full eligible/matching set returned).

**Constraints**:
- The full FR-002 eligibility conjunction (clinic verified, doctor license verified, doctor visible, active Role Assignment at that clinic) MUST be expressed as SQL/JPQL predicates evaluated by the database, never as an in-memory filter over an unfiltered fetch (spec FR-003, Constitution Principle IV).
- The endpoint MUST be reachable with zero authentication and MUST NOT accept or require any credential (spec FR-001) — its own `SecurityFilterChain`, scoped only to `/api/v1/discovery/**`, `permitAll()`s every request, mirroring the existing per-module chain pattern (`identity.account`, `patient.account`, `identity.admin` SecurityConfigs).
- No write operations of any kind — this feature is 100% read-only over existing tables.
- Text filtering MUST be case-insensitive substring matching (spec Assumptions) — implemented via SQL `LOWER(...) LIKE LOWER(CONCAT('%', :q, '%'))` (or the JPA equivalent), not application-side filtering, to keep the whole eligibility+match evaluation inside one query (Principle IV).

**Scale/Scope**: Single feature — 0 new entities, 0 new migrations, 1 new repository method (on the existing `DoctorProfileRepository` or a small new discovery-scoped repository — resolved in research.md), 1 new service, 1 new controller/endpoint, 1 new `SecurityFilterChain`, 1 new frontend page.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Test-First Development | PASS | Tests for every FR-002/FR-003 gating combination (mirroring 007's `DiscoveryEligibility*GateTest` pattern, extended with the clinic join and text filter), FR-004 live-eligibility, FR-005 text matching, and FR-001/FR-007 (no auth, no out-of-scope fields) written before implementation. |
| II. Simplicity & YAGNI | PASS | No pagination, sorting, ranking, or geocoding machinery — spec explicitly scopes these out. Reuses 007's `findDiscoveryEligible()` gate rather than re-deriving eligibility logic; adds exactly the one new join+filter query this feature actually needs. |
| III. Modular, Library-First Architecture | PASS | New `com.cms.discovery` module (service + controller + repository), testable in isolation; reads `Clinic`/`DoctorProfile`/`RoleAssignment` as read-only cross-module references, the same pattern 007/008/009 already use for `Clinic`. Own `SecurityFilterChain` scoped to its own path prefix, not folded into another module's chain. |
| IV. Data Privacy & Integrity by Design | PASS | This feature's entire reason for existing is a data-layer (not response-layer) privacy/trust guarantee — directly implements the constitution's own framing of enforcing invariants at the data layer. No patient-identifying or clinical data is touched or returned (FR-006/FR-007) — only clinic/doctor listing fields, all of which are already meant to be public once verified. |

No violations — Complexity Tracking is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/010-public-discovery-search/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── discovery-search.md
└── tasks.md
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/cms/discovery/
│   ├── DiscoveryController.java          # new — GET /api/v1/discovery/search
│   ├── DiscoverySearchService.java       # new — orchestrates the query, maps to response DTOs
│   ├── DiscoveryResultRepository.java    # new — the one new JPQL query (eligibility join + text filter)
│   ├── DiscoveryResult.java              # new — projection/DTO (doctorProfileId, doctorName, specialization, clinicId, clinicName, clinicAddress)
│   └── DiscoverySecurityConfig.java      # new — @Order(5), securityMatcher("/api/v1/discovery/**"), permitAll()
└── src/test/java/com/cms/discovery/
    └── integration/
        ├── DiscoverySearchClinicGateTest.java
        ├── DiscoverySearchLicenseGateTest.java
        ├── DiscoverySearchVisibilityGateTest.java
        ├── DiscoverySearchActiveRoleAssignmentGateTest.java
        ├── DiscoverySearchAllConditionsTest.java
        ├── DiscoverySearchDeverificationLiveUpdateTest.java
        ├── DiscoverySearchNoAuthRequiredTest.java
        ├── DiscoverySearchTextFilterTest.java
        ├── DiscoverySearchEmptyTermTest.java
        └── DiscoverySearchNoMatchTest.java

frontend/
├── src/features/discovery/
│   ├── DiscoverySearch.tsx            # new — result list + (US2) search box
│   └── api.ts                         # new — fetch client for GET /api/v1/discovery/search
└── tests/discovery/
    └── DiscoverySearch.test.tsx       # new — mirrors existing frontend/tests/<feature>/ convention
```

**Structure Decision**: A new top-level `com.cms.discovery` backend module, sibling to `com.cms.identity` and `com.cms.patient` — this is genuinely new territory (the public directory), not an extension of either existing identity or patient module, and it needs to read across both (`Clinic`+`RoleAssignment` from identity, `DoctorProfile` also from identity) without living inside either. A new `frontend/src/features/discovery/` folder follows the existing one-folder-per-feature frontend convention.

## Post-Design Constitution Re-Check

Re-evaluated after Phase 1 (data model + contracts + quickstart, below): all four principles still PASS. The single new JPQL query (data-model.md) keeps the entire eligibility+text-match evaluation inside the database, confirming Principle IV holds in the detailed design, not just at the plan-summary level.

## Complexity Tracking

*No violations — table intentionally left empty.*
