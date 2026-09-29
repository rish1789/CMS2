---

description: "Task list for Staff Login (Password or Staff Code)"
---

# Tasks: Staff Login (Password or Staff Code)

**Input**: Design documents from `/specs/006-staff-login-dual-identifier/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/staff-login.md, quickstart.md

**Tests**: Included and REQUIRED per Constitution Principle I.

**Organization**: One user story — this feature is a narrow, single-slice extension of 004's existing login endpoint. No Setup/Foundational phase needed — reuses 004's `StaffAuthController`/`AccountRepository`/`PasswordEncoder`/`StaffJwtService` directly, no new infrastructure.

## Format: `[ID] [P?] [Story] Description`

## Phase 1: User Story 1 - Staff Logs In With Staff Code Instead of Email (Priority: P1) 🎯 MVP

**Goal**: A staff member logs in with their staff code + password, resolving to the same Account and session as their email login would.

**Independent Test**: Log in with a valid staff code + correct password; confirm the same Account/session as email-based login for that Account.

### Tests for User Story 1 ⚠️

> Write these tests FIRST; confirm they FAIL before starting implementation below.

> **⚠️ Environment note (2026-09-02)**: same known sandbox issue as all prior features — Testcontainers cannot reach this sandbox's Docker Desktop daemon. T001–T003 are written, correct by code review, unexecuted (confirmed via JUnit XML report diff: pass count unchanged at 33, exactly 3 new Docker-blocked failures added, zero regressions).

- [ ] T001 [P] [US1] Integration test — staff-code login: `200`, response identical in shape/content to that Account's email-based login (FR-002) in `backend/src/test/java/com/cms/identity/account/integration/StaffCodeLoginTest.java` — **written, not yet run (see environment note above)**
- [ ] T002 [P] [US1] Integration test — wrong password with a valid staff code → `401`, same shape as the existing unknown-email failure (FR-003, FR-004) in `backend/src/test/java/com/cms/identity/account/integration/StaffCodeLoginWrongPasswordTest.java` — **written, not yet run (see environment note above)**
- [ ] T003 [P] [US1] Integration test — unrecognized staff code → `401`, same shape as unknown email (FR-004) in `backend/src/test/java/com/cms/identity/account/integration/StaffCodeLoginUnknownCodeTest.java` — **written, not yet run (see environment note above)**
- [X] T004 [P] [US1] Frontend test — `StaffLoginForm` submits with a staff-code-shaped value in the identifier field and handles success/failure the same as an email submission in `frontend/tests/staff-login/StaffLoginForm.test.tsx` — verified passing (3/3 new, 35/35 total)

### Implementation for User Story 1

- [X] T005 [US1] Add `findByStaffCode(String)` to `AccountRepository` (research.md — `existsByStaffCode` already exists from 004, no `findBy` variant yet) in `backend/src/main/java/com/cms/identity/account/AccountRepository.java`
- [X] T006 [US1] Rename `StaffLoginRequest.email` → `identifier`; update `StaffAuthController.login()` to try `findByEmail(identifier)` then `findByStaffCode(identifier)` on miss, proceeding identically from either match. **Breaking-change note**: this renames a field 004's own `StaffLoginTest.java` already sends as `email` — update that pre-existing test's request body to `identifier` in the same commit as this task, and re-run it to confirm it still passes (depends on T005) — done; that test remains Docker-blocked in the same category as before (not a new failure type)
- [X] T007 [P] [US1] Update `StaffLoginForm`'s field label/placeholder to "Email or Staff Code" in `frontend/src/features/staff-login/StaffLoginForm.tsx` — also changed input `type` from `email` to `text`, since browser email-format validation would otherwise reject a staff-code value
- [X] T008 [US1] Update the `staff-login` API client's request field name to match the renamed contract (depends on T006, T007)

**Checkpoint**: Feature complete and independently testable — this is the entire scope.

---

## Final Phase: Polish & Cross-Cutting Concerns

- [ ] T009 [P] Run all `quickstart.md` scenarios end-to-end against a running backend + frontend — **not done**; same Docker root cause as T001–T003 above
- [X] T010 Security review pass: confirm the identifier-resolution change introduces no new information-leak path between the email and staff-code lookup branches (Constitution Principle IV) — verified by inspection: no logging in `StaffAuthController` at all, both lookup branches converge to one shared `InvalidCredentialsException`

---

## Dependencies & Execution Order

### Phase Dependencies

- **User Story 1 (Phase 1)**: No blocking prerequisite phase — reuses 004's existing infrastructure directly. Tests (T001–T004) before implementation (T005–T008).
- **Polish (Final Phase)**: Depends on User Story 1.

### Parallel Opportunities

- All four test tasks (T001–T004) run in parallel.
- T007 (frontend label) can proceed in parallel with T005/T006 (backend); T008 depends on both.

---

## Implementation Strategy

1. Tests first (T001–T004), confirm they fail.
2. Implementation (T005–T008).
3. **STOP and VALIDATE**: run `quickstart.md` (T009).
4. Polish (T010).

### Notes

- This is the smallest feature in the backlog so far — verify each test actually fails before implementing (Constitution Principle I), same discipline as every larger feature.
