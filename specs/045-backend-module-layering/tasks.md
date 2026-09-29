---

description: "Task list for Backend Module Layering & Security Posture Documentation"
---

# Tasks: Backend Module Layering & Security Posture Documentation

**Input**: Design documents from `/specs/045-backend-module-layering/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md (N/A), quickstart.md

**Tests**: No new tests — the existing suite (run per module, per quickstart.md steps 1-3) is this feature's own regression proof.

**Organization**: US1 (reorganization) is broken into one task-group per module, in research.md Decision 3's smallest-first order. Each module-group is independently completable and verifiable (compile + Docker-independent subset) before moving to the next — this is the "MVP-per-increment" shape for this feature, even though all 11 groups together are what "done" means for US1.

## Phase 1-2: Setup / Foundational

Not needed — each module's move is self-contained; no shared new infrastructure.

---

## Phase 3: User Story 1 - Find "the service that owns X" by browsing (Priority: P1) 🎯 MVP

**Goal**: 11 modules reorganized into api/service/repository/domain/exception/config subpackages per research.md Decision 2's classification rules.

**Independent Test**: After each module, `gradle compileJava compileTestJava` succeeds and the Docker-independent test subset passes.

### M1: notification (11 files)

- [X] T001 [US1] Classify and `git mv` all `com.cms.notification` root files into `api/`, `service/`, `domain/`, `exception/`, `config/` per research.md Decision 2 (no `repository/` files expected — verify during the move); update each moved file's `package` declaration. Actual result: 11 files → domain(3)/exception(3)/repository(1)/service(4), no api/config files existed in this module (had none, correctly). No `NotificationController` exists (this is a service-only module per 036/037's own converged design).
- [X] T002 [US1] Fix all import statements across `backend/src/main` and `backend/src/test` that reference a moved `com.cms.notification.*` class by its old (now-incorrect) fully-qualified name. Done via scripted sibling-import-injection + project-wide FQN replace + spotless auto-prune (see tasks.md Notes).
- [X] T003 [US1] Verify per `quickstart.md` steps 1-3 for this module — all green on first attempt, no visibility issues found.

### M2: inbox (12 files)

- [X] T004 [US1] Classify and `git mv` all `com.cms.inbox` root files per research.md Decision 2; update package declarations. 12/12 classified cleanly (api 1, domain 3, exception 5, repository 1, service 2).
- [X] T005 [US1] Fix all referencing import statements project-wide. Found and fixed a real cross-package access issue: `InboxItemService.requireAuthorized` was package-private, deliberately so `InboxController` (same package before this feature) could call it directly — widened to `public` now that `InboxController`/`InboxItemService` live in separate `api`/`service` subpackages, comment updated to explain why. Also found `InboxBroadcastServiceTest.java` (a direct-child test file relying on the same bare same-package access to `InboxBroadcastService`/`InboxItemType`/`InboxItemStatus`) needed explicit imports added — generalized the reorg script (2b) to auto-inject these for all remaining modules.
- [X] T006 [US1] Verify per `quickstart.md` steps 1-3 — green after the two fixes above, including `InboxBroadcastServiceTest` itself (a genuinely dependency-free unit test) passing.

### M3: waitlist (18 files)

- [X] T007 [US1] Classify and `git mv` all `com.cms.waitlist` root files per research.md Decision 2; update package declarations. 18/18 classified cleanly (api 3, domain 2, exception 5, repository 1, service 7).
- [X] T008 [US1] Fix all referencing import statements project-wide. No visibility issues this time (unlike inbox) — clean compile first attempt.
- [X] T009 [US1] Verify per `quickstart.md` steps 1-3 — green.

### M4: clinical (21 files)

- [X] T010 [US1] Classify and `git mv` all `com.cms.clinical` root files per research.md Decision 2; update package declarations. 21/21 classified cleanly (api 3, domain 4 incl. `PrescriptionItem`, exception 5, repository 3, service 6).
- [X] T011 [US1] Fix all referencing import statements project-wide. No visibility issues — clean first attempt.
- [X] T012 [US1] Verify per `quickstart.md` steps 1-3 — green.

### M5: identity/account (15 files)

- [X] T013 [US1] Classify and `git mv` all `com.cms.identity.account` root files per research.md Decision 2 (note: `StaffJwtService`/`StaffJwtAuthenticationFilter`/`StaffAuthenticationEntryPoint` go to `config/` alongside `SecurityConfig`); update package declarations. 15/15 classified (api 2, config 4, domain 2, exception 1, repository 2, service 4). This module (`Account`/`RoleAssignment`) is referenced project-wide — widest blast radius so far.
- [X] T014 [US1] Fix all referencing import statements project-wide. Clean compile. Hit the known, pre-existing OneDrive-sync `backend/build` lock/corruption issue (memory `gradle_onedrive_build_corruption`) once during verification — unrelated to this change, resolved via `rm -rf build` once the lock cleared.
- [X] T015 [US1] Verify per `quickstart.md` steps 1-3 — green after the OneDrive lock cleared.

### M6: identity/staff (15 files)

- [X] T016 [US1] Classify and `git mv` all `com.cms.identity.staff` root files per research.md Decision 2; update package declarations. 15/15 classified (api 3, exception 9, service 2) — no domain/repository files (entities live in identity/account).
- [X] T017 [US1] Fix all referencing import statements project-wide. Clean first attempt.
- [X] T018 [US1] Verify per `quickstart.md` steps 1-3 — green.

### M7: patient/account (14 files)

- [X] T019 [US1] Classify and `git mv` all `com.cms.patient.account` root files per research.md Decision 2 (note: `JwtService`/`PatientJwtAuthenticationFilter`/`PatientAuthenticationEntryPoint` go to `config/`); update package declarations. 14/14 classified (config 4, domain 1, exception 6, repository 1, service 2) — no api/ files (controllers live in the separate, small `patient/api` module, left flat).
- [X] T020 [US1] Fix all referencing import statements project-wide. Hit the same known OneDrive `backend/build` lock again (2nd occurrence this feature) — resolved via `./gradlew --stop` + `rm -rf build`.
- [X] T021 [US1] Verify per `quickstart.md` steps 1-3 — green after clean rebuild.

### M8: patient/record (15 files)

- [X] T022 [US1] Classify and `git mv` all `com.cms.patient.record` root files per research.md Decision 2; update package declarations. 15/15 classified (api 4, domain 1, exception 7, repository 1, service 2).
- [X] T023 [US1] Fix all referencing import statements project-wide. Clean first attempt.
- [X] T024 [US1] Verify per `quickstart.md` steps 1-3 — green.

### M9: identity/admin (26 files)

- [X] T025 [US1] Classify and `git mv` all `com.cms.identity.admin` root files per research.md Decision 2 (note: `SuperAdminJwtService`/`SuperAdminJwtAuthenticationFilter`/`SuperAdminAuthenticationEntryPoint` go to `config/`; `SuperAdminAuthenticationService` goes to `service/`); update package declarations. 26/26 classified (api 4, config 4, domain 2, exception 10, service 5) — no repository/ files (Clinic/DoctorProfile repositories live in identity/clinic and identity/doctor, both left flat).
- [X] T026 [US1] Fix all referencing import statements project-wide. Clean first attempt.
- [X] T027 [US1] Verify per `quickstart.md` steps 1-3 — green.

### M10: scheduling (41 files)

- [X] T028 [US1] Classify and `git mv` all `com.cms.scheduling` root files per research.md Decision 2 (note: `BufferSlotCalculator`/`ColdStartBufferSlotCalculator` interface+impl go to `service/`); update package declarations. 41/41 classified cleanly (api 5, config 1, domain 5, exception 15, repository 3, service 12) on first attempt, zero unclassified files.
- [X] T029 [US1] Fix all referencing import statements project-wide. Clean compile, no visibility issues.
- [X] T030 [US1] Verify per `quickstart.md` steps 1-3 — green.

### M11: booking (60 files, `dto/` already separate)

- [X] T031 [US1] Classify and `git mv` all `com.cms.booking` root files per research.md Decision 2 (note: `BookingCancelledEvent` goes to `domain/`; `RiskBasedBufferSlotCalculator`/`DeVerificationCascadeListener` go to `service/`); update package declarations. 60/60 classified cleanly (api 15, config 1, domain 7, exception 21, repository 3, service 13) on the first attempt.
- [X] T032 [US1] Fix all referencing import statements project-wide. Clean compile — the largest and most business-critical module reorganized with zero visibility/compile issues.
- [X] T033 [US1] Verify per `quickstart.md` steps 1-3 — green.

**US1 complete: all 11 modules (330 files) reorganized. 1 real cross-package visibility fix needed (inbox); 2 known pre-existing OneDrive `backend/build` lock incidents unrelated to this change (both resolved via `rm -rf build`).**

**Checkpoint**: All 11 modules reorganized; full backend compiles; Docker-independent test subset passes after every module.

---

## Phase 4: User Story 2 - A load-balancer-checkable liveness endpoint (Priority: P2)

**Goal**: `/actuator/health` public, everything else denied.

### Implementation

- [X] T034 [US2] Add `spring-boot-starter-actuator` to `backend/build.gradle`.
- [X] T035 [US2] Add `management.endpoints.web.exposure.include: health` and `management.endpoint.health.show-details: never` to `application.yml`.
- [X] T036 [US2] Inspect all 5 `SecurityConfig` classes' filter-chain `@Order`/path matchers — confirmed all 6 existing chains (`identity/account` Order 1+4, `patient/account` Order 2, `identity/admin` Order 3, `discovery` Order 5, `booking` Order 6) are scoped via explicit `securityMatcher("/api/v1/**")` prefixes that don't cover `/actuator/**`, and (per an existing `application.yml` comment about `/docs`/`/api-docs`) any unmatched path falls through completely ungoverned by Spring Security, not implicitly denied. Added `com.cms.common.ActuatorSecurityConfig` (`@Order(7)`, the next free slot) explicitly permitting only `/actuator/health` and denying everything else under `/actuator/**` — defense in depth alongside the exposure restriction in T035.
- [X] T037 [US2] Verified live (started the backend via `preview_start`, confirmed in-browser): `GET /actuator/health` → 200 `{"status":"UP"}`; `GET /actuator/env`, `/actuator`, `/actuator/beans` → all 403. Startup log confirmed "Exposing 1 endpoint beneath base path '/actuator'".

**Checkpoint**: Health endpoint live and correctly scoped.

---

## Phase 5: User Story 3 - A single map of the security posture (Priority: P3)

**Goal**: `SECURITY.md` accurately indexing all 5 `SecurityConfig` classes.

### Implementation

- [X] T038 [US3] Read each of the 5 `SecurityFilterChain` beans directly (now 7, counting `identity/account`'s own 2 chains and the new `ActuatorSecurityConfig`) and wrote [SECURITY.md](../../../SECURITY.md) at the repo root with one row per chain: path pattern, realm/JWT audience, default posture — plus sections on the 3-realm JWT model, the "unmatched paths fall through ungoverned" trap, a public-surface summary, and rate limiting.
- [X] T039 [US3] Verified per `quickstart.md` step 6 by re-reading each chain's exact `requestMatchers`/`anyRequest` lines against the first draft — found and corrected 3 real inaccuracies in the *draft* before finalizing (not the live code): (1) mischaracterized clinic registration as belonging to a different chain than it does; (2) speculated a nonexistent "public-facing reads" carve-out on the patient chain; (3) invented a nonexistent "signup-equivalent onboarding" path under `/api/v1/staff/**`, which in fact has exactly one endpoint (`login`). All three fixed by re-verifying against `grep`'d matcher lines before treating the document as done — exactly the kind of self-check FR-007 requires.

**Checkpoint**: Security posture accurately documented in one place.

---

## Phase 6: Polish

- [X] T040 Run the full `quickstart.md` step 7 (`gradlew test`) and confirm the failure set is identical in kind to the pre-existing Docker/Testcontainers-only failures (no new failures introduced) — confirmed: 288 tests completed, 225 failed, the exact same count as the pre-feature baseline; grepped every failing test's XML report and confirmed 0 have a failure/error reason other than the known `Docker environment` `IllegalStateException`.
- [X] T041 Update `backlog/progress.md`'s row for `042-backend-module-layering-security-posture`.

---

## Dependencies & Execution Order

- Module groups within US1 (T001-T033) MUST proceed in the listed order (smallest-first) — not because of a technical dependency between modules' code, but because each group's own checkpoint (compile + subset pass) is the safety net for the next, larger, riskier move.
- US2 (T034-T037) and US3 (T038-T039) have no dependency on US1 or each other — could run in parallel with US1, but are sequenced after it here since US1 is this feature's primary bulk of work and MVP.
- Polish (T040-T041) depends on all three stories being complete.

## Notes

- No test-writing tasks — this is a behavior-preserving refactor; the existing suite is the test.
- Total: 41 tasks (33 for US1 across 11 modules + 4 for US2 + 2 for US3 + 2 polish).
- Given the scale (~330 files), each module-group's `git mv` + import-fix pair is executed via scripted bulk operations (not one file at a time by hand) — verified by compilation, not by manually re-reading every diff line.
