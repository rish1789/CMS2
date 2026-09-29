---

description: "Task list for Last Active ClinicAdmin Protection"
---

# Tasks: Last Active ClinicAdmin Protection

**Input**: Design documents from `/specs/005-last-active-clinicadmin-protection/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/staff-deactivation.md, quickstart.md

**Tests**: Included and REQUIRED per Constitution Principle I.

**Organization**: Two user stories — US1 (P1, deactivate Doctor/Operations — the baseline mechanism) and US2 (P1, the last-active-ClinicAdmin protection itself, layered on US1's mechanism). No new entity, no new setup — reuses 004's `StaffJwtService` and authorization pattern entirely.

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Foundational (Blocking Prerequisites)

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T001 Add `countByClinic_IdAndRoleAndActiveTrue(clinicId, role)` to `RoleAssignmentRepository` (research.md) in `backend/src/main/java/com/cms/identity/account/RoleAssignmentRepository.java`

**Checkpoint**: Foundation ready.

> **Security bug found and fixed during implementation** (not on the original task list): the new deactivate endpoint would have been **publicly accessible with no authentication at all** — `SecurityConfig`'s `authorizeHttpRequests` only explicitly protected the onboarding endpoint; everything else fell through to `.anyRequest().permitAll()`. Fixed by adding an explicit `.requestMatchers(POST, "/api/v1/clinics/*/staff/*/deactivate").authenticated()` rule, independently verified by reading the fixed `SecurityConfig.java` directly.

---

## Phase 2: User Story 1 - ClinicAdmin Deactivates a Doctor or Operations Staff Member (Priority: P1) 🎯 MVP

**Goal**: A ClinicAdmin deactivates a Doctor/Operations Role Assignment at their own clinic.

**Independent Test**: Deactivate a Doctor/Operations Role Assignment; confirm `active=false`.

### Tests for User Story 1 ⚠️

> Write these tests FIRST; confirm they FAIL before starting implementation below.

> **⚠️ Environment note (2026-09-02)**: same known sandbox issue as 001–004 — Testcontainers cannot reach this sandbox's Docker Desktop daemon. T002–T005 are written, correct by code review, unexecuted (7 new test classes, each an isolated `initializationError` from container startup, not a per-test failure — confirmed zero regressions to the pre-existing 33 passing tests).

- [ ] T002 [P] [US1] Integration test — deactivate a Doctor or Operations Role Assignment: `200`, `active=false` (FR-001) in `backend/src/test/java/com/cms/identity/staff/integration/DeactivateStaffHappyPathTest.java` — **written, not yet run (see environment note above)**
- [ ] T003 [P] [US1] Integration test — no token → `401`; non-ClinicAdmin token → `403`; cross-clinic target → `403` (FR-002) in `backend/src/test/java/com/cms/identity/staff/integration/DeactivationAuthorizationTest.java` — **written, not yet run (see environment note above)**
- [ ] T004 [P] [US1] Integration test — repeating deactivation on an already-inactive Role Assignment succeeds unchanged, no error (FR-007) in `backend/src/test/java/com/cms/identity/staff/integration/DeactivationIdempotencyTest.java` — **written, not yet run (see environment note above)**
- [ ] T005 [P] [US1] Integration test — deactivating a nonexistent Role Assignment (unknown accountId at the clinic) returns `404` in `backend/src/test/java/com/cms/identity/staff/integration/DeactivationNotFoundTest.java` — **written, not yet run (see environment note above)**
- [X] T006 [P] [US1] Frontend test — `DeactivateStaffAction` calls the endpoint and reflects the resulting state in `frontend/tests/staff-onboarding/DeactivateStaffAction.test.tsx` — verified passing (5/5 new, 32/32 total)

### Implementation for User Story 1

- [X] T007 [US1] Implement `StaffDeactivationService.deactivate(callerAccountId, clinicId, targetAccountId)` — ClinicAdmin-of-clinicId authorization check (mirrors `StaffOnboardingService`), idempotent toggle of `RoleAssignment.active` (no last-admin logic yet — added in US2) in `backend/src/main/java/com/cms/identity/staff/StaffDeactivationService.java`
- [X] T008 [US1] Implement `StaffDeactivationController` (`POST /api/v1/clinics/{clinicId}/staff/{accountId}/deactivate`) in `backend/src/main/java/com/cms/identity/staff/StaffDeactivationController.java` (depends on T007)
- [X] T009 [US1] Extend 004's `StaffExceptionHandler` with a `NOT_FOUND` mapping (depends on T008)
- [X] T010 [P] [US1] Implement `DeactivateStaffAction` React component in `frontend/src/features/staff-onboarding/DeactivateStaffAction.tsx`
- [X] T011 [US1] Extend the `staff-onboarding` API client with a `deactivateStaff` call (depends on T010)
- [X] T012 [US1] Wire the deactivate button + confirmation UI (depends on T011)

**Checkpoint**: User Story 1 is fully functional and independently testable.

---

## Phase 3: User Story 2 - Last Active ClinicAdmin Cannot Be Deactivated (Priority: P1)

**Goal**: Deactivating a clinic's last active ClinicAdmin is blocked, with no override for any role.

**Independent Test**: Attempt to deactivate a clinic's sole active ClinicAdmin; confirm `409` and the Role Assignment remains active.

### Tests for User Story 2 ⚠️

- [ ] T013 [P] [US2] Integration test — deactivating the clinic's only active ClinicAdmin returns `409 LAST_ACTIVE_CLINIC_ADMIN`; Role Assignment remains `active=true` (FR-003, FR-004). Also proves per-clinic scoping (FR-008): with a second, independent clinic that also has exactly one ClinicAdmin, confirm the first clinic's block is unaffected by the second clinic's count (and vice versa) — the check must be scoped to `{clinicId}`, not counting ClinicAdmins platform-wide in `backend/src/test/java/com/cms/identity/staff/integration/LastActiveClinicAdminBlockedTest.java` — **written, not yet run (see environment note above)**
- [ ] T014 [P] [US2] Integration test — structural proof of "no override for any role, including Super Admin": this endpoint only ever accepts a `STAFF`-audience JWT, and no feature anywhere creates a Super Admin staff Account — confirm a Super Admin (config-credential, per 003) cannot authenticate to this endpoint at all, so there is no path to attempt an override in the first place in `backend/src/test/java/com/cms/identity/staff/integration/LastActiveClinicAdminNoOverrideTest.java` — **written, not yet run (see environment note above)**; the underlying claim (Super Admin's Basic Auth header is silently ignored by `StaffJwtAuthenticationFilter`, which only recognizes `Bearer`-prefixed headers) was independently verified correct by reading that filter's source, not merely asserted
- [ ] T015 [P] [US2] Integration test — with two active ClinicAdmin Role Assignments at one clinic (created via direct test-fixture insert, since no feature builds a path to reach this state normally), deactivating one succeeds and the other remains active (FR-005) in `backend/src/test/java/com/cms/identity/staff/integration/MultipleClinicAdminsDeactivationTest.java` — **written, not yet run (see environment note above)**

### Implementation for User Story 2

- [X] T016 [US2] Extend `StaffDeactivationService` — before deactivating a `role=ClinicAdmin` target, check `RoleAssignmentRepository.countByClinic_IdAndRoleAndActiveTrue(clinicId, ClinicAdmin)` (T001); if the target is active and this count is `1`, throw `LastActiveClinicAdminException` instead of proceeding (depends on T007, T001)
- [X] T017 [US2] Add the `LAST_ACTIVE_CLINIC_ADMIN` → `409` mapping to `StaffExceptionHandler` (depends on T016)
- [X] T018 [P] [US2] Surface the `409` error message clearly in `DeactivateStaffAction` (depends on T010) — rendered in a distinct amber alert, separate from generic red-alert errors

**Checkpoint**: Both user stories functional together.

---

## Final Phase: Polish & Cross-Cutting Concerns

- [ ] T019 [P] Run all `quickstart.md` scenarios end-to-end against a running backend + frontend — **not done**; same Docker root cause as T002–T005/T013–T015 above
- [X] T020 [P] Add structured logging for deactivation attempts (outcome and error type only — never log tokens) in `StaffDeactivationService.java`
- [X] T021 Security review pass: confirm no token/credential ever appears in a log line or exception message (Constitution Principle IV)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: No dependencies — BLOCKS both user stories.
- **User Story 1 (Phase 2)**: Depends on Foundational. Tests (T002–T006) before implementation (T007–T012).
- **User Story 2 (Phase 3)**: Depends on Foundational AND on US1's `StaffDeactivationService`/Controller existing (T007, T008) — extends the same code path with the protection check.
- **Polish (Final Phase)**: Depends on both user stories.

### Parallel Opportunities

- All five US1 test tasks (T002–T006) run in parallel.
- Backend (T007–T009) and frontend (T010–T012) implementation tracks are independent and can proceed in parallel once Foundational is done.
- All three US2 test tasks (T013–T015) run in parallel.

---

## Implementation Strategy

### MVP First — User Story 1, then the protection itself (US2)

1. Complete Phase 1: Foundational.
2. Complete Phase 2: User Story 1 — tests first (T002–T006), then implementation (T007–T012).
3. **STOP and VALIDATE** US1 independently (deactivation works, but without the protection guard yet — do not ship this state, US2 is not optional).
4. Complete Phase 3: User Story 2 — tests first (T013–T015), then implementation (T016–T018).
5. Polish (T019–T021).

### Notes

- Verify each test in T002–T006 and T013–T015 actually fails before writing its corresponding implementation (Constitution Principle I).
- T014 is an unusual but important test — it proves the "no override" requirement not by testing an override attempt, but by proving the override *path doesn't exist at all* (Super Admin has no staff Account to authenticate with). Don't replace it with a weaker test that just checks the Super Admin Basic Auth credential is rejected here — the point is there's no bridge between the two identity systems at all (mirrors 002's FR-008 pattern).
