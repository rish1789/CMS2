# Handoff for Codex — CMS2 (as of 2026-09-30, `main` `363cd73`)

This note is self-contained. It assumes no prior conversation. For full history, see `HANDOFF.md` (Parts 13–14 cover the most recent work).

## 1. What this project is

CMS2 is a multi-tenant clinic management system: a Spring Boot 3.3 / Java 21 backend and a React + TypeScript (Vite, Tailwind v4) frontend. There are three roles:

- patients, who book and manage appointments;
- clinic staff (ClinicAdmin, Doctor, Operations), who handle scheduling, walk-ins, queues, cancellations and clinical notes;
- a Super Admin, who verifies clinics and doctors.

**Read first, in this order:**

1. `CLAUDE.md`: the project blueprint. It is written for Claude Code but applies equally to you; treat it as your AGENTS.md.
2. `.specify/memory/constitution.md`: governs **all** work.
3. `README.md`, then `PRODUCT.md` and `DESIGN.md` if you touch UI.
4. `CONTRIBUTING.md`: test conventions.

## 2. Non-negotiable rules

- **Spec-driven.**
  - Any **new behaviour** needs `specs/NNN-slug/spec.md` and `plan.md` (then `tasks.md`) **before** code.
  - A small fix that *restores behaviour an existing spec already requires*, and that a failing test already encodes, may skip a new spec. Cite the spec and FR in the commit message.
  - If unsure, write the spec.
- **Test-first** (constitution I).
  - Write or run the failing test first, then fix.
  - Record the observed red and green results. A test that is green before the change is a *characterization* guard, not proof of the fix; say so.
- **Races are closed at the data layer** (constitution IV). Existing precedents:
  - guarded `@Modifying` status updates (`claimIfOffered`, `expireIfOffered`);
  - partial unique indexes;
  - the `PatientAccountRepository.findWithLockById` row lock (060 and 066).
- **Tenant scoping.** Every clinic-scoped query must be scoped to the clinic.
- **Clinical notes and prescriptions are write-once.** No edit path.
- **Flyway migrations are forward-only.** Never edit or delete a shipped migration (`backend/src/main/resources/db/migration`, currently up to V41); add a new one.
- **Module boundaries:** cross-module effects go through events. Example: `BookingCancelledEvent` → `WaitlistBumpListener`.
- **`AFTER_COMMIT` listeners that write** need `@Transactional(propagation = REQUIRES_NEW)`, or their writes are silently lost. This bit the codebase twice.
- **Owner's git and file rules:**
  - Do not use `git reset`, `git clean`, `git stash` or `git checkout`. Use `git worktree add` for other branches.
  - Never run `spotlessApply` across the tree; always pass `-x spotlessApply`, and format only files you touched.
  - Read every file back after writing it, to check it wasn't truncated.
  - Inspect `git status` and confirm the exact changed files before committing.
  - Don't modify unrelated files.
- **Merging:** use merge commits (not squash), so that stacked branches stay valid. Only merge when the owner says so.

## 3. Spec-kit without Claude's slash commands

The workflow lives in `.claude/skills/speckit-*/SKILL.md` (specify, clarify, plan, tasks, analyze, implement, converge). Read those files as plain instructions and follow them by hand:

- **Templates:** `.specify/templates/{spec,plan,tasks,checklist}-template.md`.
- **Numbering:** sequential. Feature **067** is implemented and merged. Inspect `specs/` before allocating the next number.
- **Active feature:** write `.specify/feature.json` as `{"feature_directory": "specs/NNN-slug"}`.
- **Scripts:** the `.specify/scripts/powershell/*.ps1` helpers need `pwsh`, which may not be installed. Doing their steps by hand is fine.
- **Worked example:** `specs/066-patient-linking-race/`. It has a spec, a plan, research with rejected alternatives, and tasks with observed test results.

## 4. Commands

```bash
# Backend (from backend/). Integration tests use Testcontainers, so Docker must be running.
./gradlew spotlessCheck -x spotlessApply
./gradlew test -x spotlessApply --tests "*SomeTest"          # one class
./gradlew test --rerun -x spotlessApply --tests "*SomeTest"  # force a real re-run (bypass the Gradle cache)
./gradlew test -x spotlessApply                              # full suite: 1,071 tests at this baseline, ~19 min in CI

# Frontend (from frontend/)
npm ci && npx tsc -b && npm run lint && npx vitest run       # 441 tests
```

**Docker in a Linux sandbox:** start the daemon with `DOCKER_MIN_API_VERSION=1.24 dockerd &`, because Testcontainers 1.21's docker-java is too old for Docker 29's default minimum API. Never `pkill -f` a broad pattern; it can kill the daemon or your own shell. Kill by PID.

**Test shapes** (`CONTRIBUTING.md`):
- pure Mockito unit tests;
- `@WebMvcTest` contract tests;
- `integration/` classes that extend an `Abstract*IntegrationTest` base. Each base has a Postgres `@Container` and `@DirtiesContext(AFTER_CLASS)`. A subclass `@AfterEach` runs **before** the base cleanup, so use it to delete child rows first.

## 5. Current state

- **Verified baseline:** `main` at `363cd73`, after PRs #20, #23 and #22. [CI run 36694968509](https://github.com/rish1789/CMS2/actions/runs/36694968509) passed: backend **1,071 passed, 0 failed, 0 skipped**, counted from individual test-result lines; frontend **441 passed across 78 files**. Formatting, type-check, lint and frontend dependency audit passed. Backend OWASP scanning was skipped because `NVD_API_KEY` is not configured.
- **Resolved:** #20 fixed booking rate limiting and the waitlist 409 expectation, and updated undici; #22 disables scheduled sweeps in tests; #23 implements 067 queue-token serialization and atomic queue booking to prevent orphan tokens. No revert is needed because #23 merged before #22.
- **PR #19:** documentation refreshed onto this baseline, plus synchronization of the numeric protection-setting test before editing. Run 36697988071 observed 440 frontend passes and one failure (input remained 8 instead of 5); the unchanged test passed 10 repeated local runs, so this is test hardening, not a locally reproduced product fix. Its old [CI run 36605800183](https://github.com/rish1789/CMS2/actions/runs/36605800183) had **1,047 passed, 3 failed, 0 skipped**: booking rate-limit concurrency, queue-token issuance concurrency and the waitlist 404/409 expectation. Those failures preceded the fixes above. Check the latest PR checks before merging; the main baseline is not a substitute for PR CI.
- **Remaining limitations:** cancellation does not take the session lock used by 067, so issuance concurrent with cancellation remains out of scope. `PartialSessionCancellationRangeTest` passed in the baseline; one passing run does not establish that its historical intermittency is eliminated.
- **Time-of-day fixtures:** use future-dated sessions for new tests. Disabling sweeps does not fix assertions against already elapsed slots.
- **Open:** Dependabot PRs #1-#10 require individual review and fresh CI. CI timeout, action upgrades and the stray gitlink remain separate housekeeping work. SEC-03 and PB-005 require owner decisions.

## 6. Suggested next work, in priority order

1. Confirm PR #19's latest frontend and backend checks, then let the owner review and merge it.
2. Review the smaller dependency changes first (#2, #10, #5), validating each against current main. Review the related JJWT updates (#3 and #4) together for version compatibility.
3. Evaluate Testcontainers 2 (#6), then the larger frontend and Spring Boot upgrades (#7-#9 and #1) as separate compatibility changes; passing CI alone is not a migration review.
4. In a separate housekeeping PR, consider a backend CI timeout, supported action upgrades and removal of the stray gitlink without deleting the owner's nested working files. Enabling OWASP requires the owner's repository secret configuration.
5. Run convergence reviews for 065, 066 and 067. Investigate the historical partial-cancellation flake if it recurs; do not claim #22 definitively fixed it without evidence.
6. Leave SEC-03 (per-clinic pricing) and PB-005 (time zones) pending the owner's decisions. Do not merge or deploy on the owner's behalf.

## 7. Working with the owner

- **Recommend, don't list options.** Give a recommendation rather than a survey of options, and ask only when a decision is genuinely theirs.
- **Honest reporting.** Report failures with the actual output. Separate "verified" from "expected". Correct your own earlier claims explicitly when they turn out wrong.
- **Timestamps** in IST.
- **Long runs:** the owner likes live progress on long test runs (counts passed and failed so far).
- **Pre-existing failures:** keep them separate from failures caused by your change. Prove it by running the same test on `main` without your change.
