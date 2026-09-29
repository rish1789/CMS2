# 09 — Testing and Quality

Labels: [RUNTIME] = observed while running · [CODE] = read in source · [INFERRED] = drawn from code or docs, not proven · [UNKNOWN] = could not be determined.

## 1. Tooling [CODE]

| Concern | Backend | Frontend |
|---|---|---|
| Test framework | JUnit 5, Mockito, AssertJ, spring-security-test, Testcontainers (Postgres 16) | Vitest 4, @testing-library/react, user-event, jsdom |
| Test shapes | (1) pure Mockito unit, (2) `@WebMvcTest` contract, (3) full-context `integration/` with Testcontainers (per `CONTRIBUTING.md`) | Component tests with mocked `fetch` |
| Lint / format | Spotless (Java, `spotlessApply` runs automatically before every `compileJava`) | oxlint (jsx-a11y, react, import rules) |
| Type check | javac | `tsc -b` |
| Coverage | JaCoCo reporting only, no gate (`build.gradle`) | none configured |
| Dependency scan | OWASP dependency-check (CI, only if `NVD_API_KEY`) | `npm audit --audit-level=high` (CI) |
| End-to-end | **none** | **none** (no Playwright or Cypress) |
| CI | `.github/workflows/ci.yml`: spotlessCheck, test, JaCoCo upload, optional dependency-check | npm ci, lint, audit, `tsc -b`, test |

## 2. Inventory [CODE]

- **Backend:** 355 test source files, 984 `@Test` methods:
  - 39 Mockito unit classes
  - 22 `@WebMvcTest` contract classes
  - 249 concrete integration classes, plus 31 abstract integration bases
- **Frontend:** 78 test files in `frontend/tests/<feature>/` (mirrors `src/features`), 436 tests.
  - Features with **no** frontend test directory: `patient-clinics`, `super-admin`.

## 3. Results in this session [RUNTIME]

| Command | Result |
|---|---|
| `gradle -p backend spotlessCheck` | BUILD SUCCESSFUL |
| `gradle -p backend test -x spotlessApply --continue` | 620 test entries: **371 passed**, **249 failed**. Every failure is `initializationError` with "Could not find a valid Docker environment", one per integration class. **0 assertion failures.** Docker is not installed on this machine. |
| JaCoCo (unit + contract only, because integration did not run) | **41.1 % lines, 37.6 % branches.** Highest: `scheduling/service` 73 %, `protection/service` 71 %. **Lowest (≤ 12 %):** `waitlist/service` 8 %, `inbox/service` 2 %, `patient/record/service` 2 %, `identity/doctor` 1 %, `common` 1 %, `identity/account/service` 11 %, `identity/staff/api` 12 %, `inbox/api` 0 %, `waitlist/api` 0 %, `identity/admin/api` 0 %. These packages are covered **only** by the unexecuted integration tests. |
| `npx tsc -b` | exit 0 |
| `npm run lint` | exit 0, 24 warnings (for example `react(set-state-in-effect)` ×4) |
| `npm run test` (full suite) | **430 passed, 6 failed** (5 s timeouts), 148.96 s. Isolated rerun of the 3 affected files: **16/16 pass**. See BUG-006. |
| `npm audit --audit-level=high` | exit 0; 2 moderate advisories (react-router) |
| Backend startup | Started; Flyway validated; `/api-docs` 200 |
| CI history | [UNKNOWN] — `gh` is not installed and the GitHub connector is not authorized. `main` has 2 commits, and nearly all current code is uncommitted, so CI has **never** run against the current code. |

The integration suite (about 70 % of backend test classes) has therefore **never executed** on the current code in any environment visible to this audit. `backlog/progress.md` repeatedly records the same limitation.

## 4. Coverage of critical workflows

"Meaningful automated coverage" below means an executed test that asserts the rule. Integration tests exist for most rules but are unexecuted.

| Workflow | Unit / contract (executed) | Integration (written, unexecuted) | Frontend | Gap |
|---|---|---|---|---|
| Appointments (staff and patient fixed-time) | `FeeResolutionServiceTest`, `BookingProtectionServiceTest`, `RejectedClinicBookingRefusalTest`, patient booking limit and rate limit contract tests | `StaffBooking*` (5), `PatientBooking*` (8), `PatientOpenSlotListing`, `PatientSlotBookingDateLogic`, `BookingLimitConcurrency` | patient-booking 7, staff-booking 2 | No `StaffBookingService` or `PatientBookingService` unit test. **No test asserts that elapsed same-day slots are excluded** (BUG-005). No test for the phone collision (PB-001). |
| Walk-ins | `FrontDeskWalkInServiceTest`, `FrontDeskWalkInControllerContractTest`, `QueueSlotServiceWalkInTest`, `UntimedSlotGuardsTest`, `WalkInSelfCancelContractTest` | `FrontDeskWalkInRegistration`, `WalkInLineLifecycle`, `VisitReasonConstraint` | 3 files | No test for a past-session walk-in (PB-004) or concurrent walk-ins in the same session (PB-003) |
| Queue | `QueuePositionServiceTest`, `QueueSelfCancelContractTest` | `QueueBooking*`, `QueueSlotIssuance*` (3), `QueuePosition*` (5), `QueueSendInComplete`, `QueueTokenMigration` | queue-position 1 | The concurrency test for token issuance through a *transactional caller* is unexecuted |
| Doctor availability (schedules, generation) | `ScheduleServiceTest`, `ScheduleSessionGeneratorRejectedClinicTest`, `SlotGenerationServiceTest`, `ScheduleDeletionServiceTest` | `CreateSchedule*` (6), `SessionGeneration*` (7), `SlotPreGeneration*` (3) | scheduling 3 | Time-zone behaviour untested |
| Live status / delay | `SessionDelayServiceTest`, `SessionLiveStatusServiceTest` (uses a `Clock`), 2 contract tests | `SessionDelay*`, `SessionLiveStatus*` | session-delay 5 | — |
| Cancellation | `BookingCancellationServiceTest`, `BatchBookingCancellationServiceTest`, `ClinicRejectionCascadeServiceTest` | `SessionCancellation*` (6), `PartialSessionCancellation*` (7), `PatientBookingCancellation*` (5), `StaffBookingCancellation*` (3) | 5 files | **No test asserts a cancelled session or range is not re-bookable** (BUG-002/003). The existing tests encode "slot → OPEN" as expected behaviour [INFERRED from service semantics]. |
| Rescheduling | — | — | — | Feature absent (by decision) |
| No-show | `NoShowDetectionServiceTest` | `NoShowDetection`, `SlotAppearedRemovesNoShowEligibility` | — | No time-zone test; no test of scan cost |
| Buffer logic | — | — | — | Removed feature; the tests were removed with it (058) |
| Check-in / completion | `SlotAppearedServiceTest`, `SlotCompletionServiceTest`, `SlotAutoCompletionServiceTest`, `SlotAppearedControllerContractTest` | `SlotCompletion*` (3), `SlotAutoCompletion` | day-sheet 2 | — |
| Authentication | `StaffAuthServiceTest`, `StaffAuthControllerTest`, `PatientAccountServiceTest`, `PatientAccountContractTest`, `RegisterClinicContractTest` | `StaffLogin`, `StaffCodeLogin*`, `PatientLogin`, `SuperAdminResolvedLogin`, `RateLimiting` | staff-login 2, patient-account 1 | Frontend token expiry is untested |
| Authorization | Contract tests cover 401/403 for the endpoints they target (43 test files reference unauthenticated or `isUnauthorized`) | `*Authorization*` classes (about 12), `ProtectionFlagTenantIsolation` | — | **No test enumerates all mapped endpoints against the security allowlist.** That is how BUG-001 (7 endpoints) goes undetected. |
| Waitlist | — (8 % service coverage when integration is not run) | `Waitlist*` (10) | waitlist 3 | Depends entirely on unexecuted integration tests |
| Inbox / SSE | — (2 %) | `Inbox*` (7) | inbox 2 | Same |
| Patient linking / records | — (2 %) | `PatientLinking*` (8) | patient-search 4 | Same |

## 4a. BUG-006 investigation (Phase 1, 2026-09-29)

**Classification:** intermittent, load-dependent. On the same machine the suite had 6 timeouts on 2026-09-28 (148 s) and passed 436/436 on 2026-09-29 (53 s), both before any change.

**Checked and excluded [RUNTIME/CODE]:**
- fake timers (none in the affected files)
- unawaited user actions (all awaited)
- insufficient synchronisation (`waitFor`/`findBy` used on every async assertion)
- leaked mocks (`mockReset` in `beforeEach`)
- missing cleanup (RTL auto-cleanup with Vitest globals)
- component timers or intervals (none in RegistrationForm, ScheduleForm or ExternalRecordReferenceForm)

**Root cause (measured):**
- Per-test cost is dominated by `user.type` over about 60 characters. user-event v14's default `delay: 0` awaits a `setTimeout(0)` between keystrokes, and each keystroke re-renders the form.
- In isolation the affected tests took 1.0–1.3 s. Under full-suite parallel load they slowed about 4×, past the 5 s default.

**Change:** `userEvent.setup({ delay: null })` in 6 typing-heavy files: the 3 affected, plus StaffOnboarding, PrescriptionForm and ConsultationNoteForm, which measured as the next-slowest typing tests.
- Every keystroke event is still dispatched.
- Measured effect in isolation: about 2–3× faster (for example 1.34 s → 0.55 s; 8.5 s → 4.1 s across 26 tests).
- The global timeout is unchanged.

**Verification:** 4 consecutive full runs after the change, all 436/436 (48–55 s). The slowest remaining tests under full load are about 2.3–2.6 s. `DoctorScheduleManager` (about 2.3 s) is not typing-driven and was left unchanged; there was no evidence of a cause.

**Residual risk:** a heavier-loaded machine could still slow tests. This change removes the dominant avoidable cost but cannot make timing load-independent.

## 5. Quality observations

1. **Unexecuted verification.** The largest share of backend assurance (249 classes) has not run. Priority: run it in CI or on a Docker-capable machine before relying on the "Converged" statuses in `backlog/progress.md`.
2. **Timing-sensitive frontend tests** (BUG-006) make CI results load-dependent.
3. **No end-to-end coverage** of any cross-role flow. For example: walk-in registered → inbox item → sent in → completed → live status updates for the patient.
4. **No architectural tests** (for example ArchUnit) for the module-boundary or controller-layering rules that `CLAUDE.md` asserts, or for the security allowlist.
5. **Coverage is reporting-only** by explicit decision (`build.gradle` comment, 044-ci-quality-gates).
6. **Constitution compliance.** Test-first for new backend behaviour is documented as mandatory. The test inventory is consistent with that, but correctness of the integration layer is unverified.
