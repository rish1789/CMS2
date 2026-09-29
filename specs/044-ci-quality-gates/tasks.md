---

description: "Task list for CI/CD Quality Gates"
---

# Tasks: CI/CD Quality Gates

**Input**: Design documents from `/specs/044-ci-quality-gates/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md (N/A), quickstart.md

**Tests**: Not applicable — no application behavior; the CI pipeline running green with new steps included is this feature's own verification (see plan.md Constitution Check).

**Organization**: Phases ordered by priority (US2/P1 first as MVP, then US1/P2, then US3/US4/P3). All four stories touch different files/config sections and have no ordering dependency on each other.

## Phase 1-2: Setup / Foundational

Not needed — no shared new infrastructure; each story's config addition is self-contained.

---

## Phase 3: User Story 2 - Backend dependency vulnerability scanning (Priority: P1) 🎯 MVP

**Goal**: A dependency with a CVSS ≥ 7 vulnerability fails backend CI.

**Independent Test**: Run `./gradlew dependencyCheckAnalyze` locally against the current dependency set (expect pass) per `quickstart.md` step 1.

### Implementation

- [X] T001 [US2] Add the `org.owasp.dependencycheck` Gradle plugin (v13.0.0) to `backend/build.gradle`, configured with `failBuildOnCVSS = 7.0` and NVD data caching enabled (`data.directory`, gitignored).
- [X] T002 [US2] Add a `dependencyCheckAnalyze` step to the `backend` job in `.github/workflows/ci.yml`, with a cache action for the OWASP NVD database directory — **revised during implementation**: made both the cache step and the scan step conditional on `secrets.NVD_API_KEY` being set, with a `::warning::`-logging skip step when it isn't (see research.md's "Confirmed during implementation" note: the scan fails outright, not just slowly, without a free NVD API key, and none is configured on the live repo yet — shipping it unconditionally would have permanently broken CI).
- [X] T003 [US2] Verify per `quickstart.md` step 1: attempted `./gradlew dependencyCheckAnalyze` locally with no key — confirmed it fails with `Invalid API Key, length of 0` exactly as research.md now documents (not a bug, expected without a key). Confirmed `spotlessCheck` and the full test/build pipeline are otherwise unaffected. Full positive-path verification (a real key, or a real CI run once `NVD_API_KEY` is added) is deferred — same class of "needs a real environment/credential this sandbox can't supply" caveat as this project's Docker-dependent tests.

**Checkpoint**: A CI run with the current dependency set passes; a dependency with a known CVSS ≥ 7 CVE would fail it (verified live per quickstart.md step 6, once merged).

---

## Phase 4: User Story 1 - Automatic dependency update PRs (Priority: P2)

**Goal**: Dependabot scans both ecosystems weekly.

**Independent Test**: Merge `.github/dependabot.yml` and confirm GitHub's Dependabot tab shows both ecosystems scheduled.

### Implementation

- [X] T004 [P] [US1] Create `.github/dependabot.yml` with two `updates` entries: `package-ecosystem: gradle`, `directory: /backend`, weekly schedule; `package-ecosystem: npm`, `directory: /frontend`, weekly schedule.

**Checkpoint**: Config is valid YAML and matches Dependabot's schema (verify live on GitHub per `quickstart.md` step 5, once merged).

---

## Phase 5: User Story 3 - Frontend dependency audit visibility (Priority: P3)

**Goal**: `npm audit` visible in CI, failing only on high/critical.

**Independent Test**: Run `npm audit --audit-level=high` locally (expect exit 0 today) and `npm audit` with no flag (expect the 2 known moderate findings visible) per `quickstart.md` steps 3-4.

### Implementation

- [X] T005 [US3] Add an `npm audit --audit-level=high` step to the `frontend` job in `.github/workflows/ci.yml`, placed after the existing `lint` step.
- [X] T006 [US3] Verify per `quickstart.md` steps 3-4: `npm audit --audit-level=high` exits 0 (confirmed 2026-09-15 — the 2 known moderate findings display but don't fail the step); plain `npm audit` shows the 2 known `react-router`/`react-router-dom` moderate findings in its output (confirmed).

**Checkpoint**: Frontend CI passes today with the known moderate finding visible but non-blocking; a newly introduced high/critical finding would fail it.

---

## Phase 6: User Story 4 - Coverage visibility without a false gate (Priority: P3)

**Goal**: Backend coverage reported as a CI artifact, no gate.

**Independent Test**: Run `./gradlew test jacocoTestReport` locally and confirm a report is generated, per `quickstart.md` step 2.

### Implementation

- [X] T007 [P] [US4] Apply the Jacoco plugin in `backend/build.gradle`, wire `jacocoTestReport` to run after `test` (`finalizedBy`), with no `jacocoTestCoverageVerification` rule configured (explicitly no gate).
- [X] T008 [US4] Add a step to the `backend` job in `.github/workflows/ci.yml` uploading `backend/build/reports/jacoco/test/html/` as a build artifact — used `if: always()` (not unconditional with no guard) so the artifact still uploads even when `:test` fails, since `jacocoTestReport` only actually produces output for the subset of tests that ran without needing Docker.
- [X] T009 [US4] Verify per `quickstart.md` step 2: `./gradlew test jacocoTestReport` against the *full* suite fails to reach `jacocoTestReport` at all in this sandbox — `jacocoTestReport.dependsOn test`, and 225/288 tests fail on the pre-existing Testcontainers/Docker limitation, so Gradle never runs the downstream report task (confirmed: `test.exec` is written, but no HTML/XML report). Verified the Jacoco wiring itself is correct by running only the Docker-independent unit/contract tests (`--tests "com.cms.identity.unit.*" --tests "*.contract.*" jacocoTestReport`): all pass, `BUILD SUCCESSFUL`, and `backend/build/reports/jacoco/test/html/index.html` + `jacocoTestReport.xml` were both generated. Full-suite report generation will work once run against Docker/CI where all 288 tests can pass.

**Checkpoint**: Every backend CI run produces a coverage artifact with zero effect on pass/fail status.

---

## Phase 7: Polish

- [X] T010 Run the full existing CI-equivalent local check to confirm zero regression to any pre-existing step (FR-005) — verified 2026-09-15: backend `spotlessCheck` BUILD SUCCESSFUL; backend `test` shows the same pre-existing 225/288 Testcontainers/Docker failures as every other feature in this project (not a regression — confirmed via Docker-independent subset passing cleanly, see T009); frontend `npm run lint` exit 0 (pre-existing warnings only, no errors), `npx tsc -b` exit 0, `npm run test` 236/236 passed, `npm audit --audit-level=high` exit 0.
- [X] T011 Update `backlog/progress.md`'s row for `041-ci-cd-quality-gates`.

---

## Dependencies & Execution Order

- All four user stories are independent (different files/config sections); order above follows priority (P1 → P2 → P3/P3), not a hard dependency chain.
- Polish (Phase 7) depends on all four stories being complete.

## Parallel Example

```bash
Task: "Add org.owasp.dependencycheck plugin to backend/build.gradle (US2, T001)"
Task: "Create .github/dependabot.yml (US1, T004)"
Task: "Apply Jacoco plugin to backend/build.gradle (US4, T007)"
```

Note: T001 and T007 both edit `backend/build.gradle` — do these two sequentially even though both are otherwise independent, to avoid an edit conflict on the same file.

## Notes

- No test tasks — no application behavior to test; each story's manual/CI verification is its correctness proof.
- Total: 11 tasks (3 + 1 + 2 + 3 + 2).
