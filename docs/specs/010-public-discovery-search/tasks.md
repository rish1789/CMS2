---

description: "Task list for Public Discovery Search"
---

# Tasks: Public Discovery Search

**Input**: Design documents from `/specs/010-public-discovery-search/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/discovery-search.md, quickstart.md

**Tests**: Included — Constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this project; every prior converged feature wrote tests before implementation.

**Organization**: Tasks are grouped by user story (US1 = P1 verification gating, US2 = P2 text-search filtering) per spec.md.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2)

## Path Conventions

Web app split per plan.md: `backend/src/main/java/com/cms/discovery/`, `backend/src/test/java/com/cms/discovery/integration/`, `frontend/src/features/discovery/`, `frontend/tests/discovery/`.

---

## Phase 1: Setup

**Purpose**: New module scaffolding shared by both stories — no schema change, no new dependency.

- [X] T001 [P] Create `DiscoveryResult.java` (read-only DTO — `doctorProfileId`, `doctorName`, `specialization`, `clinicId`, `clinicName`, `clinicAddress`, per data-model.md) in `backend/src/main/java/com/cms/discovery/DiscoveryResult.java`
- [X] T002 [P] Create `DiscoverySecurityConfig.java` — new `SecurityFilterChain` bean, `@Order(5)`, `securityMatcher("/api/v1/discovery/**")`, `csrf().disable()`, stateless session, `authorizeHttpRequests(auth -> auth.anyRequest().permitAll())` — mirroring `com.cms.patient.account.SecurityConfig`'s pattern (research.md) — in `backend/src/main/java/com/cms/discovery/DiscoverySecurityConfig.java`

**Checkpoint**: Module skeleton exists; no behavior yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared test fixture both stories' integration tests build on.

**⚠️ CRITICAL**: No user story test can be written until this phase is complete.

- [X] T003 Create `AbstractDiscoveryIntegrationTest.java` — Testcontainers Postgres base class (mirroring `AbstractPatientRecordIntegrationTest` from 009) with helper builders for: a Super-Admin-verified `Clinic`, an `Account` + `DoctorProfile` (license verified, visible) + active Doctor-role `RoleAssignment` at that clinic — the fully-eligible baseline every gate test starts from and individually breaks — in `backend/src/test/java/com/cms/discovery/integration/AbstractDiscoveryIntegrationTest.java`

**Checkpoint**: Foundation ready — User Story 1 can now be built.

---

## Phase 3: User Story 1 - Only Fully-Verified Providers Are Discoverable (Priority: P1) 🎯 MVP

**Goal**: A public, unauthenticated `GET /api/v1/discovery/search` (no filter) returns exactly the clinic/doctor pairings where clinic-verified AND license-verified AND visible AND active-Role-Assignment all hold, evaluated live on every call.

**Independent Test**: Seed every combination of the four gating conditions per quickstart.md Scenario 1/2/4; call the endpoint with no `q` and no auth; assert the result set matches exactly the eligible subset.

### Tests for User Story 1 (write first, confirm they FAIL before implementation)

- [X] T004 [P] [US1] Integration test: unverified clinic excludes clinic and all its doctors, in `backend/src/test/java/com/cms/discovery/integration/DiscoverySearchClinicGateTest.java`
- [X] T005 [P] [US1] Integration test: unverified doctor license excludes that doctor from an otherwise-verified clinic, in `backend/src/test/java/com/cms/discovery/integration/DiscoverySearchLicenseGateTest.java`
- [X] T006 [P] [US1] Integration test: `visible = false` excludes an otherwise-eligible doctor, in `backend/src/test/java/com/cms/discovery/integration/DiscoverySearchVisibilityGateTest.java`
- [X] T007 [P] [US1] Integration test: an inactive Role Assignment excludes an otherwise-eligible doctor, in `backend/src/test/java/com/cms/discovery/integration/DiscoverySearchActiveRoleAssignmentGateTest.java`
- [X] T008 [P] [US1] Integration test: a doctor meeting all four conditions appears in results, carrying their clinic's identity, in `backend/src/test/java/com/cms/discovery/integration/DiscoverySearchAllConditionsTest.java`
- [X] T009 [P] [US1] Integration test: a doctor holding active Role Assignments at two clinics (one verified, one not) appears exactly once, scoped to the verified clinic, in `backend/src/test/java/com/cms/discovery/integration/DiscoverySearchMultiClinicScopeTest.java`
- [X] T010 [P] [US1] Integration test: de-verifying a clinic (or resetting a doctor's license) after a prior eligible result removes it from the very next call, AND re-verifying it afterward makes it reappear on the next call after that — both directions, no caching either way — in `backend/src/test/java/com/cms/discovery/integration/DiscoverySearchDeverificationLiveUpdateTest.java`
- [X] T011 [P] [US1] Integration test (`@AutoConfigureMockMvc`, no `Authorization` header set): request succeeds `200` with correctly gated results, in `backend/src/test/java/com/cms/discovery/integration/DiscoverySearchNoAuthRequiredTest.java`

### Implementation for User Story 1

- [X] T012 [US1] Implement `DiscoveryResultRepository` with the `search(String q)` JPQL query (research.md) — full eligibility conjunction (`role_assignment.active`, `role.role = Doctor`, `clinic.verified`, `doctor_profile.license_verified`, `doctor_profile.visible`) plus the `q`-blank-passthrough branch (text-match branch built now since it's one query, exercised by US1's own tests only via a blank/null `q`) — in `backend/src/main/java/com/cms/discovery/DiscoveryResultRepository.java` (depends on T001)
- [X] T013 [US1] Implement `DiscoverySearchService.search(String q)` — trims `q`, treats null/blank as no filter, delegates to the repository, returns `List<DiscoveryResult>` unchanged — in `backend/src/main/java/com/cms/discovery/DiscoverySearchService.java` (depends on T012)
- [X] T014 [US1] Implement `DiscoveryController` — `GET /api/v1/discovery/search`, optional `q` request param, delegates to `DiscoverySearchService`, returns `200` with the JSON array (empty array when no matches, never an error) — in `backend/src/main/java/com/cms/discovery/DiscoveryController.java` (depends on T013, T002)
- [X] T015 [P] [US1] Create `DiscoverySearch.tsx` — fetches `GET /api/v1/discovery/search` (no filter) on mount, renders each result's clinic name/address and doctor name/specialization, no search input yet — in `frontend/src/features/discovery/DiscoverySearch.tsx`
- [X] T016 [P] [US1] Create `api.ts` fetch client — `searchDiscovery(q?: string)` calling `GET /api/v1/discovery/search` with an optional `q` query param, typed `DiscoveryResult[]` response (contracts/discovery-search.md) — in `frontend/src/features/discovery/api.ts` (depends on T015 for the interface it's consumed by, parallel-safe as a separate file)
- [X] T017 [US1] Frontend test: renders the fetched, already-eligible-only result list with no login prompt anywhere on the page, in `frontend/tests/discovery/DiscoverySearch.test.tsx` (depends on T015, T016)

**Checkpoint**: User Story 1 fully functional and independently testable — the public directory is live, correctly gated, no search box yet.

---

## Phase 4: User Story 2 - Finding a Specific Provider by Specialization, Name, or Location (Priority: P2)

**Goal**: The same endpoint's optional `q` parameter narrows the already-eligible result set by case-insensitive substring match against specialization, doctor name, clinic name, or clinic address.

**Independent Test**: Per quickstart.md Scenario 3 — seed several eligible doctors with distinct specialization/name/clinic values, issue searches with each kind of term, confirm only matches (still eligible-only) return; confirm an unmatched term returns an empty array, not an error.

### Tests for User Story 2 (write first, confirm they FAIL before this phase's own additions — the underlying query already exists from US1, so these tests validate the `q` branch specifically)

- [X] T018 [P] [US2] Integration test: `q` matching specialization/doctor-name/clinic-name/clinic-address each independently narrow results to the matching subset, in `backend/src/test/java/com/cms/discovery/integration/DiscoverySearchTextFilterTest.java`
- [X] T019 [P] [US2] Integration test: `q` matching nothing returns `200` with an empty array, in `backend/src/test/java/com/cms/discovery/integration/DiscoverySearchNoMatchTest.java`
- [X] T020 [P] [US2] Integration test: an absent, empty, or whitespace-only `q` returns the identical full eligible set as no `q` at all, in `backend/src/test/java/com/cms/discovery/integration/DiscoverySearchEmptyTermTest.java`

### Implementation for User Story 2

- [X] T021 [US2] Verify/finish the `q` text-match branch in `DiscoveryResultRepository.search()` (case-insensitive `LIKE` across all four fields per data-model.md) against T018–T020 — extend if T012's initial pass left it incomplete, in `backend/src/main/java/com/cms/discovery/DiscoveryResultRepository.java`
- [X] T022 [US2] Add a search input to `DiscoverySearch.tsx` — controlled text field, re-fetches via `api.ts`'s `searchDiscovery(q)` on change (debounced), updates the rendered list, shows a "no matches" empty state — in `frontend/src/features/discovery/DiscoverySearch.tsx` (depends on T015, T016, T021)
- [X] T023 [US2] Extend `DiscoverySearch.test.tsx` — typing a term narrows the rendered list; an unmatched term shows the empty state — in `frontend/tests/discovery/DiscoverySearch.test.tsx` (depends on T017, T022)

**Checkpoint**: Both user stories independently functional — full search-and-filter directory, still fully public.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [X] T024 Run `quickstart.md` Scenarios 1–5 end-to-end against a real (or Testcontainers) Postgres instance; record pass/fail per scenario in `specs/010-public-discovery-search/quickstart.md`'s own notes or `backlog/progress.md`
- [X] T025 Run full backend build (`/tmp/gradle-8.10/bin/gradle build` — see project memory: use this instead of `./gradlew`; `rm -rf backend/build` first if OneDrive sync corruption is suspected) and full frontend suite (`npm test` in `frontend/`); confirm both green with zero regressions in prior features' tests

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup (T001) for the DTO the fixture builders will eventually return — BLOCKS both user stories' test-writing.
- **User Story 1 (Phase 3)**: Depends on Foundational. No dependency on US2.
- **User Story 2 (Phase 4)**: Depends on US1's repository/service/controller/frontend page existing (it extends the same query and the same component) — not independently buildable from a blank slate, but independently *testable and demonstrable* once built (its own tests target only the `q`-filter behavior).
- **Polish (Phase 5)**: Depends on both stories being complete.

### Parallel Opportunities

- T001, T002 in parallel (different files).
- T004–T011 (all US1 tests) in parallel — different files, all depend only on T003.
- T015, T016 in parallel (different files) within US1 implementation.
- T018–T020 (all US2 tests) in parallel — different files, all depend on US1's implementation (T012–T014) being in place.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 → Phase 2 → Phase 3 (US1). **STOP and VALIDATE**: quickstart.md Scenarios 1, 2, 4, 5 (minus the search box) pass. This alone delivers the core trust guarantee and a browsable public directory.

### Incremental Delivery

2. Add Phase 4 (US2): quickstart.md Scenario 3 passes. Delivers the actual "search" usability on top of the already-correct, already-public directory.
3. Phase 5: full-suite verification and quickstart sign-off.
