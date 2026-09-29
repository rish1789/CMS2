---

description: "Task list for Super Admin Clinic Verification"
---

# Tasks: Super Admin Clinic Verification

**Input**: Design documents from `/specs/003-super-admin-verification/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/clinic-verification.md, quickstart.md

**Tests**: Included and REQUIRED per Constitution Principle I.

**Organization**: Two user stories — US1 (P1, list + verify) and US2 (P2, un-verify + cascade-trigger event). No new entity — this feature operates on 001's existing `Clinic.verified` field.

**Note on FR-005/SC-003**: enforcing `verified` at the discovery data-query level has no tasks in this file, by design. This feature only guarantees the flag itself is correct; the query enforcement is 035-public-discovery-search's responsibility.

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Setup

- [X] T001 [P] Add `admin.super-admin.username`/`admin.super-admin.password` configuration properties (environment-driven, no default committed) to `backend/src/main/resources/application.yml`
- [X] T002 [P] Create the `frontend/src/features/clinic-verification/` directory scaffold

---

## Phase 2: Foundational (Blocking Prerequisites)

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T003 [P] Implement `SuperAdminSecurityConfig` — HTTP Basic Auth filter chain (`@Order(3)`, after 001's and 002's chains) guarding `/api/v1/admin/**` against the configured credentials (research.md) in `backend/src/main/java/com/cms/identity/admin/SuperAdminSecurityConfig.java` — confirmed stateless, credentials re-validated every request, matching research.md
- [X] T004 [P] Implement `ClinicDeVerifiedEvent` (Spring `ApplicationEvent`, carries `clinicId` + `occurredAt` — FR-008, data-model.md) in `backend/src/main/java/com/cms/identity/admin/ClinicDeVerifiedEvent.java`
- [X] T005 [P] Configure exception-handling scaffold for admin error shapes (404 clinic not found) in `backend/src/main/java/com/cms/identity/admin/AdminExceptionHandler.java`

**Checkpoint**: Foundation ready.

---

## Phase 3: User Story 1 - Super Admin Verifies a Pending Clinic (Priority: P1) 🎯 MVP

**Goal**: Super Admin lists unverified clinics and marks one verified.

**Independent Test**: `GET /api/v1/admin/clinics/pending` shows an unverified clinic; `POST .../verify` sets `verified=true`; the clinic no longer appears in the pending list.

### Tests for User Story 1 ⚠️

> Write these tests FIRST; confirm they FAIL before starting implementation below.

> **⚠️ Environment note (2026-09-02)**: same known sandbox issue as 001/002 — Testcontainers cannot reach this sandbox's Docker Desktop daemon. T006–T008 are written, compile, and are correct by direct code review, but unexecuted. Also noted by the implementing fork: this sandbox's Gradle `build/` directory under OneDrive intermittently corrupts mid-run (`Cannot snapshot ...: not a regular file`, a OneDrive sync/placeholder-file artifact, unrelated to any code) — `rm -rf backend/build` before a run reliably fixes it.

- [ ] T006 [P] [US1] Integration test — `GET /api/v1/admin/clinics?verified=false` returns only unverified clinics with registration details (FR-001); `?verified=true` returns only verified ones (supports US2's un-verify UI) in `backend/src/test/java/com/cms/identity/admin/integration/PendingClinicsListTest.java` — **written, not yet run (see environment note above)**
- [ ] T007 [P] [US1] Integration test — verify sets `true`; repeating the call on an already-verified clinic succeeds identically (idempotent, FR-007) in `backend/src/test/java/com/cms/identity/admin/integration/VerifyClinicTest.java` — **written, not yet run (see environment note above)**
- [ ] T008 [P] [US1] Integration test — both endpoints reject requests with no credentials AND with valid 001 staff-Account credentials, both as `401` (FR-004) in `backend/src/test/java/com/cms/identity/admin/integration/AdminAuthorizationTest.java` — **written, not yet run (see environment note above)**
- [X] T009 [P] [US1] Frontend test — `PendingClinicsList` renders clinics, verify action removes one from the list on success in `frontend/tests/clinic-verification/PendingClinicsList.test.tsx` — verified passing (6/6 new, 20/20 total incl. 001/002)

### Implementation for User Story 1

- [X] T010 [US1] Implement `ClinicVerificationService.listByVerified(boolean verified)` and `.verify(clinicId)` — reuses 001's `ClinicRepository` directly (no new repository) in `backend/src/main/java/com/cms/identity/admin/ClinicVerificationService.java` — fixed a real bug found during implementation: originally used `findAll().stream().filter(...)`, loading every clinic to filter client-side; added `ClinicRepository.findByVerified(boolean)` (additive to 001) and switched to a real `WHERE` query
- [X] T011 [US1] Implement `ClinicVerificationController` (`GET /api/v1/admin/clinics?verified={true|false}`, `POST /api/v1/admin/clinics/{id}/verify`) in `backend/src/main/java/com/cms/identity/admin/ClinicVerificationController.java` (depends on T010) — confirmed matches the revised query-param contract exactly, matching the already-built frontend
- [X] T012 [US1] Wire `AdminExceptionHandler` mapping for 404 (clinic not found) (depends on T011, T005)
- [X] T013 [P] [US1] Implement `PendingClinicsList` React component — single list view with Pending/Verified tabs, a Super Admin login gate (username/password in `sessionStorage`, never `localStorage`), and contextual verify/un-verify actions in `frontend/src/features/clinic-verification/PendingClinicsList.tsx`
- [X] T014 [US1] Implement clinic-verification API client (`listClinics`, `verifyClinic`, `unverifyClinic`; HTTP Basic Auth credentials passed per call) in `frontend/src/features/clinic-verification/api.ts` (depends on T013)
- [X] T015 [US1] Wire the verify action button and list refresh-on-success (depends on T014)

**Checkpoint**: User Story 1 is fully functional and independently testable.

---

## Phase 4: User Story 2 - Super Admin Un-Verifies a Clinic (Priority: P2)

**Goal**: Super Admin revokes a clinic's verified status, triggering the de-verification cascade's event hook.

**Independent Test**: `POST /api/v1/admin/clinics/{id}/unverify` on a verified clinic sets `verified=false` and publishes `ClinicDeVerifiedEvent` exactly once; repeating the call does not re-publish it.

### Tests for User Story 2 ⚠️

- [ ] T016 [P] [US2] Integration test — un-verify sets `false` and publishes `ClinicDeVerifiedEvent` exactly once (assert via a test `@EventListener`); repeating the call on an already-unverified clinic succeeds identically WITHOUT re-publishing the event (FR-007, FR-008) in `backend/src/test/java/com/cms/identity/admin/integration/UnverifyClinicTest.java` — **written, not yet run (see environment note above)**; correctly uses Spring Test's `ApplicationEvents`/`@RecordApplicationEvents` to assert exactly-once, confirmed by code review

### Implementation for User Story 2

- [X] T017 [US2] Add `.unverify(clinicId)` to `ClinicVerificationService` — publishes `ClinicDeVerifiedEvent` only on an actual `true → false` transition (depends on T010, T004)
- [X] T018 [US2] Add `POST /api/v1/admin/clinics/{id}/unverify` to `ClinicVerificationController` (depends on T017, T011)
- [X] T019 [P] [US2] Add the un-verify action button to the "Verified" tab of `PendingClinicsList` (T013) — no separate component needed now that the list already covers both tabs (depends on T013)

**Checkpoint**: Both user stories functional together.

---

## Final Phase: Polish & Cross-Cutting Concerns

- [ ] T020 [P] Run all `quickstart.md` scenarios end-to-end against a running backend + frontend — **not done**; same Docker root cause as T006–T008/T016 above
- [X] T021 [P] Add structured logging for verify/unverify actions (clinic ID and outcome only — never log Super Admin credentials) in `ClinicVerificationService.java`
- [X] T022 Security review pass: confirm Super Admin credentials never appear in a log line, error response, or exception message (Constitution Principle IV)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS both user stories.
- **User Story 1 (Phase 3)**: Depends on Foundational. Tests (T006–T009) before implementation (T010–T015).
- **User Story 2 (Phase 4)**: Depends on Foundational AND on US1's `ClinicVerificationService`/`Controller` existing (T010, T011) — un-verify extends the same service/controller.
- **Polish (Final Phase)**: Depends on both user stories.

### Parallel Opportunities

- T001, T002 (Setup) run in parallel.
- T003, T004, T005 (Foundational) run in parallel — different files.
- All four US1 test tasks (T006–T009) run in parallel.
- Backend (T010–T012) and frontend (T013–T015) implementation tracks are independent and can proceed in parallel once Foundational is done.

---

## Implementation Strategy

### MVP First — User Story 1

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational.
3. Complete Phase 3: User Story 1 — tests first (T006–T009), then implementation (T010–T015).
4. **STOP and VALIDATE** US1 independently.
5. Layer in User Story 2 (T016–T019) — un-verify + cascade event.
6. Polish (T020–T022).

### Notes

- Verify each test in T006–T009 and T016 actually fails before writing its corresponding implementation (Constitution Principle I).
- T016 is the test proving FR-007's idempotency AND FR-008's event-trigger together — don't split it without keeping both assertions.
