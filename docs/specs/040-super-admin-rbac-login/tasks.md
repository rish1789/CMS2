---

description: "Task list for Super Admin RBAC Login & Console Access"
---

# Tasks: Super Admin RBAC Login & Console Access

**Input**: Design documents from `/specs/040-super-admin-rbac-login/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/clinic-portal-login.md, quickstart.md

**Tests**: Included as first-class tasks — Constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this project; every new/changed behavior below gets a failing test before its implementation task.

**Organization**: Tasks are grouped by user story (US1/US2/US3, matching spec.md's priorities) after a shared Foundational phase.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1, US2, or US3 — omitted for Setup/Foundational/Polish tasks

## Path Conventions

Web app per plan.md: `backend/src/main/java/com/cms/...`, `backend/src/test/java/com/cms/...`, `frontend/src/...`, `frontend/tests/...`.

---

## Phase 1: Setup

- [X] T001 Add `admin.super-admin.jwt.secret` (dev-only default, `SUPER_ADMIN_JWT_SECRET` env override, mirroring `staff.jwt.secret`/`patient.jwt.secret`) to `backend/src/main/resources/application.yml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared backend/frontend plumbing every user story below depends on. No user story can be implemented or tested until this phase is complete.

- [X] T002 [P] Create `SuperAdminJwtService` in `backend/src/main/java/com/cms/identity/admin/SuperAdminJwtService.java` — mirrors `StaffJwtService` exactly: HMAC secret from `admin.super-admin.jwt.secret`, audience claim `SUPER_ADMIN`, `issueToken(String username)` (subject = username, no account UUID exists), `validateAndGetUsername(String token): Optional<String>`, 12-hour TTL (data-model.md)
- [X] T002a Create `SuperAdminAuthenticationService` in `backend/src/main/java/com/cms/identity/admin/SuperAdminAuthenticationService.java` — the single exposed contract `com.cms.identity.account` is allowed to call into this module through: `Optional<String> authenticate(String identifier, String password)`, internally composing the existing `superAdminUserDetailsService` bean (`passwordEncoder.matches(...)`) and T002's `SuperAdminJwtService.issueToken(...)`; returns empty on no match. Keeps both internals encapsulated in `com.cms.identity.admin` (research.md R2, fixes the Analyze-stage C2 finding: no other module may inject `superAdminUserDetailsService`/`SuperAdminJwtService` directly). Depends on T002.
- [X] T003 [P] Create `SuperAdminJwtAuthenticationFilter` in `backend/src/main/java/com/cms/identity/admin/SuperAdminJwtAuthenticationFilter.java` — mirrors `StaffJwtAuthenticationFilter` exactly: reads `Bearer` header, on valid token sets `SecurityContextHolder` with the username as principal and `ROLE_SUPER_ADMIN` authority; deliberately NOT a `@Component` (same `@WebMvcTest` auto-registration hazard `StaffJwtAuthenticationFilter`'s own Javadoc documents)
- [X] T004 [P] Create `SuperAdminAuthenticationEntryPoint` in `backend/src/main/java/com/cms/identity/admin/SuperAdminAuthenticationEntryPoint.java` — mirrors `StaffAuthenticationEntryPoint` exactly: writes `{"error":"UNAUTHORIZED"}` with `401` instead of Spring Security's default Basic-Auth challenge
- [X] T005 [P] Extend `StaffLoginResponse` in `backend/src/main/java/com/cms/identity/account/dto/StaffLoginResponse.java` — add `String role` field; change `accountId` to nullable (data-model.md)
- [X] T006 [P] Create `frontend/src/features/super-admin/token.ts` — `StoredSuperAdminSession { token, username }`, `loadSuperAdminSession()`/`storeSuperAdminSession()`, `sessionStorage` key `cms.superAdminToken` (mirrors `staff-login/token.ts` exactly, research.md R6)
- [X] T007 [P] Add `RequireSuperAdminSession` guard to `frontend/src/routes/guards.tsx` — mirrors `RequireStaffSession`/`RequirePatientSession` exactly, redirects to `/staff/login` when `loadSuperAdminSession()` is empty (research.md R6)
- [X] T008 [P] Create `frontend/src/features/staff-login/destination.ts` — exports `decideClinicPortalDestination(role: 'STAFF' | 'SUPER_ADMIN'): string`, returning `/super-admin-console` or `/staff` (research.md R5)
- [X] T009 [P] Extend `LoginStaffResponse`/`LoginStaffRequest` types in `frontend/src/features/staff-login/api.ts` — add `role: 'STAFF' | 'SUPER_ADMIN'` to `LoginStaffResponse`, `accountId: string | null`

**Checkpoint**: Foundational plumbing exists — user story implementation can now begin.

---

## Phase 3: User Story 1 - Super Admin signs in through the Clinic Portal and lands on the console (Priority: P1) 🎯 MVP

**Goal**: A Super Admin can log in on the existing Clinic Portal (`/staff/login`) and land on a working `/super-admin-console`.

**Independent Test**: Enter valid Super Admin credentials at `/staff/login` and confirm the browser lands on `/super-admin-console` with clinic-verification data actually loaded.

### Tests for User Story 1 ⚠️ write first, confirm they fail

- [X] T010 [P] [US1] Integration test: `POST /api/v1/staff/login` with the configured Super Admin credential resolves `role: "SUPER_ADMIN"`, `accountId: null`, and a JWT whose audience is `SUPER_ADMIN` — new test class in `backend/src/test/java/com/cms/identity/account/integration/SuperAdminResolvedLoginTest.java`
- [X] T011 [P] [US1] Integration test: `GET /api/v1/admin/clinics?verified=false` with a valid Super Admin bearer JWT (from T010's login, or issued directly via `SuperAdminJwtService`) returns `200` — add to `backend/src/test/java/com/cms/identity/admin/integration/PendingClinicsListTest.java` (this file's other, pre-existing tests keep working unmodified because T014a migrates their shared `superAdminAuthHeader()` helper atomically with T014's chain switch, not in a later phase)
- [X] T012 [P] [US1] Frontend test: submitting valid Super Admin credentials on `StaffLoginForm` calls `onSuccess` with `role: 'SUPER_ADMIN'` and the page navigates to `/super-admin-console` — extend `frontend/tests/staff-login/StaffLoginForm.test.tsx`

### Implementation for User Story 1

- [X] T013 [US1] Modify `StaffAuthController.login` in `backend/src/main/java/com/cms/identity/account/StaffAuthController.java` — inject the new `SuperAdminAuthenticationService` (T002a) only (never the raw `superAdminUserDetailsService`/`SuperAdminJwtService` beans directly, per research.md R2); before the existing `AccountRepository` lookup, call `superAdminAuthenticationService.authenticate(identifier, password)`; on a present result, return `role: "SUPER_ADMIN"`, `accountId: null`, `email: <configured username>`, `token: <the returned JWT>`; otherwise fall through unchanged to the existing Account lookup and return `role: "STAFF"` (research.md R2, R4)
- [X] T014 [US1] Modify `SuperAdminSecurityConfig.adminFilterChain` in `backend/src/main/java/com/cms/identity/admin/SuperAdminSecurityConfig.java` — remove `.httpBasic(withDefaults())` and `.userDetailsService(...)`; add `.addFilterBefore(new SuperAdminJwtAuthenticationFilter(superAdminJwtService), UsernamePasswordAuthenticationFilter.class)` and `.exceptionHandling(ex -> ex.authenticationEntryPoint(superAdminAuthenticationEntryPoint))`; keep `.anyRequest().authenticated()` unchanged (research.md R3)
- [X] T014a [US1] **Must land in the same change as T014, never separately** — modify `backend/src/test/java/com/cms/identity/admin/integration/AbstractAdminIntegrationTest.java`: autowire `SuperAdminJwtService`, change `superAdminAuthHeader()` to return `"Bearer " + superAdminJwtService.issueToken(SUPER_ADMIN_USERNAME)` instead of building a Basic Auth header (research.md R9). T014 alone disables Basic Auth entirely, and ~15+ pre-existing integration test classes (`PendingDoctorsListTest`, `VerifyClinicTest`, `UnverifyClinicTest`, `DoctorVerify*Test`, `EditDoctor*Test`, `RetentionPurgeAuthorizationTest`, etc.) authenticate successfully only through this one inherited helper — landing T014 without T014a breaks all of them (Analyze-stage C1 finding).
- [X] T015 [US1] Modify `frontend/src/features/staff-login/StaffLoginForm.tsx` — branch on `response.role`: `'STAFF'` stores via existing `storeStaffSession`, `'SUPER_ADMIN'` stores via new `storeSuperAdminSession` (T006); pass `role` through to `onSuccess`
- [X] T016 [US1] Modify `frontend/src/routes/staff/StaffLoginPage.tsx` — `onSuccess` now receives `role` and navigates to `decideClinicPortalDestination(role)` (T008) instead of the hardcoded `/staff`
- [X] T017 [US1] Modify `frontend/src/features/clinic-verification/api.ts` — replace `AdminCredentials`/`authHeader`/Basic-Auth with a `token: string` parameter and `Authorization: Bearer ${token}` (research.md R7, R8)
- [X] T018 [US1] Modify `frontend/src/features/clinic-verification/PendingClinicsList.tsx` — remove the inline username/password login form; read the token via `loadSuperAdminSession()` (T006) and pass it to the `api.ts` calls (T017); route entry is already gated by `RequireSuperAdminSession` (T007/T021), so no in-component "not logged in" branch is needed
- [X] T019 [US1] Modify `frontend/src/features/doctor-verification/api.ts` — same change as T017
- [X] T020 [US1] Modify `frontend/src/features/doctor-verification/PendingDoctorsList.tsx` — same change as T018
- [X] T020a [US1] Modify `frontend/src/features/session-generation/api.ts` — same change as T017
- [X] T020b [US1] Modify `frontend/src/features/session-generation/TriggerSessionGeneration.tsx` — same change as T018
- [X] T021 [US1] Modify `frontend/src/App.tsx` — replace the unguarded `<Route path="/admin" element={<AdminShell />}>` block with `<Route path="/super-admin-console" element={<RequireSuperAdminSession />}><Route element={<AdminShell />}>...</Route></Route>` (same child routes, new prefix)
- [X] T022 [US1] Modify `frontend/src/routes/admin/AdminShell.tsx` — update the 3 nav `<Link>` paths from `/admin/...` to `/super-admin-console/...`

**Checkpoint**: User Story 1 fully functional — a Super Admin can log in and use the console end-to-end.

---

## Phase 4: User Story 2 - Staff logins on the shared Clinic Portal, and patient logins on their own portal, are unaffected (Priority: P2)

**Goal**: Prove the Clinic Portal change is invisible to staff and patients beyond the additive `role` field.

**Independent Test**: Log in as an existing ClinicAdmin/Doctor/Operations Account and confirm the same `/staff` landing as before; log in as a patient and confirm nothing changed.

### Tests for User Story 2 ⚠️ write first, confirm they fail (then pass with no production-code changes beyond US1's)

- [X] T023 [P] [US2] Extend `backend/src/test/java/com/cms/identity/account/integration/StaffLoginTest.java` — assert the success response now also carries `role: "STAFF"` alongside the existing `accountId`/`email`/`token` assertions
- [X] T024 [P] [US2] Extend `frontend/tests/staff-login/StaffLoginForm.test.tsx` — assert a staff login still calls `onSuccess`/navigates to `/staff` with `role: 'STAFF'`, unchanged destination

### Implementation for User Story 2

- [X] T025 [US2] Verify (no code change expected) that `frontend/src/routes/patient/PatientLoginPage.tsx`, `frontend/src/features/patient-account/*`, and `backend/src/main/java/com/cms/patient/account/**` are untouched by every task above; run the existing patient test suite and record zero regressions (FR-002/FR-007/SC-004)

**Checkpoint**: Staff and patient behavior confirmed unchanged; only the additive `role` field is new.

---

## Phase 5: User Story 3 - The Super Admin console rejects anyone who isn't a signed-in Super Admin (Priority: P1)

**Goal**: Prove the frontend guard and backend chain actually reject everyone else, in both the browser and at the API.

**Independent Test**: While logged out, navigate to `/super-admin-console` and confirm redirection; call an admin endpoint with no credential, a staff JWT, a patient JWT, and a correct-credential Basic Auth header, and confirm all four are rejected identically.

### Tests for User Story 3 ⚠️ write first, confirm they fail

- [X] T027 [US3] Modify `backend/src/test/java/com/cms/identity/admin/integration/AdminAuthorizationTest.java` — change the two "valid staff Account credentials rejected" tests to send a staff JWT (issued via the autowired `StaffJwtService`) as `Bearer` instead of a staff Basic Auth header; add a new test asserting a request with a correct-credential Basic Auth header (`Basic <base64(SUPER_ADMIN_USERNAME:SUPER_ADMIN_PASSWORD)>`) is now also rejected with `401` (FR-012). Note: `AbstractAdminIntegrationTest.superAdminAuthHeader()` itself was already migrated off Basic Auth by T014a (Phase 3) — not repeated here.
- [X] T028 [P] [US3] Integration test: an expired/malformed Super Admin JWT is rejected `401` — add to `backend/src/test/java/com/cms/identity/admin/integration/AdminAuthorizationTest.java`
- [X] T029 [P] [US3] Integration test: a valid Patient Account JWT sent to `/api/v1/admin/**` is rejected `401` — add to `AdminAuthorizationTest.java`
- [X] T030 [P] [US3] Frontend test: `RequireSuperAdminSession` redirects to `/staff/login` when no Super Admin session exists, including when a valid staff session exists but no Super Admin session — new `frontend/tests/routes/guards.test.tsx` (create if it doesn't already cover `RequireStaffSession`/`RequirePatientSession`; otherwise extend the existing guard test file)

### Implementation for User Story 3

- [X] T031 [US3] Run the full existing `/api/v1/admin/**`-dependent integration test suite (`PendingDoctorsListTest`, `VerifyClinicTest`, `UnverifyClinicTest`, `DoctorVerify*Test`, `EditDoctor*Test`, `RetentionPurgeAuthorizationTest`, and the rest of `backend/src/test/java/com/cms/identity/admin/integration/`) and confirm all pass unmodified against T014a's updated `superAdminAuthHeader()` — no source change expected in this task, verification only

**Checkpoint**: Rejection behavior is proven on both the frontend guard and every backend admin endpoint, including the now-removed Basic Auth path.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T032 [P] Run `quickstart.md` Scenarios 1–4 end-to-end against a real running backend + frontend
- [X] T033 Full backend build (`compileJava`, `compileTestJava`, `spotlessCheck`) green
- [X] T034 Full frontend suite (`vitest run`, `tsc -b`, lint) green, zero regressions in the pre-existing suite

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories.
- **User Story 1 (Phase 3)**: Depends on Foundational. No dependency on US2/US3.
- **User Story 2 (Phase 4)**: Depends on Foundational + US1's `role` field existing (T013) to write meaningful assertions against, but touches no production code of its own.
- **User Story 3 (Phase 5)**: Depends on Foundational + US1 (T013/T014/T014a — the resolution logic, JWT chain, and migrated test helper it builds on already exist).
- **Polish (Phase 6)**: Depends on US1, US2, US3 all complete.

### Within Each User Story

- Tests (T010–T012, T023–T024, T027–T030) MUST be written and confirmed failing before their corresponding implementation tasks.
- US1's T013 and T014 touch disjoint files and can run in parallel with each other, but **T014a must land in the same change as T014, never separately or later** (Analyze-stage C1 fix — see T014a's own note); all three admin-screen pairs (T017–T018, T019–T020, T020a–T020b) touch disjoint files and can run in parallel with each other, but each pair's `api.ts` task precedes its own component task.

### Parallel Opportunities

- Phase 2: T002, T003–T009 (8 tasks across 8 distinct files, zero interdependency) can all run in parallel; T002a depends on T002 (needs `SuperAdminJwtService` to exist) and so follows it, not parallel with it.
- Within US1: T010/T011/T012 (tests, distinct files); T017/T019/T020a (the three `api.ts` files) in parallel with each other.
- Within US3: T028/T029/T030 (distinct test files/assertions) in parallel; T027 builds on T014a (already landed in Phase 3).

---

## Parallel Example: Foundational Phase

```bash
# Fully parallel (no interdependency):
Task: "Create SuperAdminJwtService in backend/.../admin/SuperAdminJwtService.java"
Task: "Create SuperAdminJwtAuthenticationFilter in backend/.../admin/SuperAdminJwtAuthenticationFilter.java"
Task: "Create SuperAdminAuthenticationEntryPoint in backend/.../admin/SuperAdminAuthenticationEntryPoint.java"
Task: "Extend StaffLoginResponse in backend/.../account/dto/StaffLoginResponse.java"
Task: "Create frontend/src/features/super-admin/token.ts"
Task: "Add RequireSuperAdminSession to frontend/src/routes/guards.tsx"
Task: "Create frontend/src/features/staff-login/destination.ts"
Task: "Extend LoginStaffResponse in frontend/src/features/staff-login/api.ts"

# Sequential (depends on SuperAdminJwtService above):
Task: "Create SuperAdminAuthenticationService in backend/.../admin/SuperAdminAuthenticationService.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Setup (T001) → Foundational (T002–T009, T002a) → User Story 1 (T010–T022, T014a).
2. **STOP and VALIDATE**: run quickstart.md Scenario 1 manually — Super Admin logs in, reaches a working console.
3. This is a real, demoable MVP: the actual security gap (Basic Auth, no login step) is closed at this point, even before US2/US3's tests are written.

### Incremental Delivery

1. Foundational → US1 (MVP: Super Admin can log in and use the console).
2. US2 (regression proof staff/patient are unaffected) — safe to demo/merge alongside US1, since it adds no behavior.
3. US3 (rejection proof — T027's remaining precision fix plus T028/T029/T030's new negative-path tests) — this closes the loop on FR-009/FR-010/FR-012 with explicit evidence, not just "it should work because the mechanism changed."

### Note on story ordering

US3's tests are technically exercising behavior US1's implementation (T013/T014) already produces as a structural side effect (a JWT-audience-based filter simply never authenticates a differently-audienced token). The one task that could NOT wait for US3 — migrating `AbstractAdminIntegrationTest.superAdminAuthHeader()` off Basic Auth — was moved into US1 itself as T014a (Analyze-stage C1 fix), since ~15+ pre-existing admin test classes depend on that one helper and would otherwise fail the moment T014 lands. US3 is still listed last because everything that remains there (T027's precision fix, T028–T030's new negative-path tests) is genuinely just verification of already-built behavior, not a hidden regression risk.
