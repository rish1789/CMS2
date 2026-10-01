# Handoff for Codex — CMS2 (as of 2026-10-01, Spring Boot 4 migration on branch `claude/spring-boot-4`)

This note is self-contained. It assumes no prior conversation. For full history, see `HANDOFF.md` (Parts 13–16 cover the most recent work).

## 1. What this project is

CMS2 is a multi-tenant clinic management system: a Spring Boot 4.1 / Java 21 backend (Gradle 8.14.3 wrapper) and a React + TypeScript (Vite, Tailwind v4) frontend. There are three roles:

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
- **Numbering:** sequential. Features up to **067** are implemented and merged, so the next free number is **068**. Inspect `specs/` before allocating it anyway.
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

**Docker:**
- Testcontainers is now **2.0.5** (#25).
- The old `DOCKER_MIN_API_VERSION=1.24 dockerd &` workaround was needed for 1.21's docker-java against Docker 29. According to #25's commit message, the full suite ran on Docker 29.8.1 *without* the workaround. That was on Windows; it has not been re-checked in a Linux sandbox. If Testcontainers cannot reach Docker there, fall back to the workaround.
- Never `pkill -f` a broad pattern; it can kill the daemon or your own shell. Kill by PID.
- **Maven Central rate limit:** it can return HTTP 429 to sandboxes. Wait a few minutes and retry. Do **not** use `--refresh-dependencies`, which re-requests everything and makes the rate limiting worse.

**Test shapes** (`CONTRIBUTING.md`):
- pure Mockito unit tests;
- `@WebMvcTest` contract tests;
- `integration/` classes that extend an `Abstract*IntegrationTest` base. Each base has a Postgres `@Container` and `@DirtiesContext(AFTER_CLASS)`. A subclass `@AfterEach` runs **before** the base cleanup, so use it to delete child rows first.

## 5. Current state

- **`main` is at `8df853b`.** There are **no open PRs**: every Dependabot PR is merged or closed.
- **CI on `main`:**
  - The last completed run is on `d025e65`, after the react-router 7 merge: [run 36745328664](https://github.com/rish1789/CMS2/actions/runs/36745328664), success.
  - The run on `8df853b` (TypeScript 7), [run 36753530061](https://github.com/rish1789/CMS2/actions/runs/36753530061): **success**.
- **Last counted baseline:** `363cd73` ([run 36694968509](https://github.com/rish1789/CMS2/actions/runs/36694968509)).
  - Backend: **1,071 passed, 0 failed**. Frontend: **441 passed across 78 files**.
  - Backend OWASP scanning is skipped because the `NVD_API_KEY` repository secret is not configured. It is the owner's decision whether to add it.
- **Resolved on 2026-09-30:**
  - **#20:** the booking rate limit is exact under concurrency; the waitlist not-staffed case returns 409 (the owner's decision); undici bumped. #21 was closed as part of #20.
  - **#23:** feature 067, which fixes PB-003 queue-token issuance under concurrency and prevents orphan tokens.
  - **#22:** scheduled sweeps are disabled in integration tests.
  - **#19:** docs, plus hardening of the numeric protection-setting test.
  - **#24:** CI `timeout-minutes` (45 min backend, 15 min frontend), Node 24 action majors, and removal of the stray `docs/.claude - Copy` gitlink.
  - **#25:** Testcontainers 2.0.5 (#6 closed as superseded).
  - **#26:** the frontend CI runs on Node 24.
  - **Dependabot:** #2, #4 (all three JJWT modules to 0.13.0, so #3 was closed), #5, #7 (react-router-dom 7), #8 (Vitest 5), #9 (TypeScript 7) and #10.
- **TypeScript 7 (#9):** the native compiler, installed per platform (Windows included). Before merge it was verified against `main` with react-router 7:
  - `tsc -b` reports 0 errors, and a planted type error was correctly reported (TS2322);
  - lint is clean, 441/441 tests pass, the production build succeeds, and there are 0 high-severity audit findings.
  - Nothing in the project uses the removed TypeScript JS API.
- **Spring Boot 4 (#1) was closed without merging** ([comment](https://github.com/rish1789/CMS2/pull/1#issuecomment-5916579809)) and replaced by a planned migration on branch `claude/spring-boot-4` (2026-10-01). It targets Boot **4.1.1** and is **owner-merged only**:
  - **Build:** Gradle wrapper 8.10 → **8.14.3**. Starters changed: `starter-web` → `starter-webmvc`, `flyway-core` → **`starter-flyway`** (without it, migrations stop running), springdoc 2.6.0 → **3.1.1**, and added the `starter-webmvc-test` and `starter-security-test` test starters.
  - **Code:** one import (`ApiErrorController` now uses `org.springframework.boot.webmvc.error.ErrorController`). Tests: `@MockBean` → `@MockitoBean` (`org.springframework.test.context.bean.override.mockito`), and the `@WebMvcTest`/`@AutoConfigureMockMvc` imports moved to `org.springframework.boot.webmvc.test.autoconfigure` (47 files).
  - **`spring.jackson.use-jackson2-defaults: true`.** Boot 4 serializes with Jackson 3, whose `FAIL_ON_NULL_FOR_PRIMITIVES` default turned a request omitting a primitive field (for example `FrontDeskWalkInRequest.confirmDuplicate`) into a 400. The setting keeps JSON behaviour identical to Boot 3. Moving to Jackson 3 defaults would be a separate, deliberate change.
  - **`.claude/launch.json`** now runs `./backend/gradlew.bat` instead of a standalone Gradle 8.10, which Boot 4 rejects.
  - **Verified:**
    - full backend suite **1,070 passed, 0 failed, 1 skipped** (the skip is a test that by design only runs between 02:30 and 21:00);
    - runtime on Postgres 16: Flyway applied V1–V41 to an empty database, Hibernate `validate` passed, 0 ERROR log lines;
    - patient, staff and Super Admin logins each reach a protected endpoint (200);
    - cross-realm and no-token requests return 401; CORS preflights return 403 for a foreign origin and 200 for an allowed one; `/actuator/env` returns 403.
- **Remaining limitations:**
  - Cancellation does not take the session lock used by 067, so token issuance concurrent with cancellation is out of scope.
  - `PartialSessionCancellationRangeTest` passed in the baseline, but its historical intermittency is not proven fixed.
- **Time-of-day fixtures:** use future-dated sessions for new tests. Many fixtures generate *today's* 09:00–13:00 session, so an assertion that needs a bookable slot fails for any run after 09:00. Disabling sweeps does not fix that.
- **Owner decisions pending:** SEC-03 (per-clinic pricing), PB-005 (time zones) and the `NVD_API_KEY` secret.

## 6. Suggested next work, in priority order

1. **The Spring Boot 4 migration PR (branch `claude/spring-boot-4`) awaits the owner's review and merge.** After it merges:
   - confirm CI on `main`;
   - confirm the owner's Windows launch config (`.claude/launch.json`, now `./backend/gradlew.bat`) starts the backend. The wrapper downloads Gradle 8.14.3 on first use. This could not be tested on Windows from the cloud sandbox.
2. **Watch for the midnight-wrap test pattern.** Building `HH:MM` fixture times as `now ± N hours` on today's date wraps past midnight. Two instances were fixed in the Boot 4 PR: `SlotCompletionServiceTest` now takes date and time from one `LocalDateTime`, and `FrontDeskWalkInPage.test.tsx` pins `Date` to midday. Prefer the same approaches in new tests.
3. Run convergence reviews for 065, 066 and 067 (`.claude/skills/speckit-converge/SKILL.md`). Investigate the historical partial-cancellation flake if it recurs.
4. Consider a spec (068) for the gap between cancellation and 067's session lock, if the owner wants it closed.
5. Leave SEC-03, PB-005 and the OWASP secret pending the owner's decisions. Do not merge or deploy on the owner's behalf.

## 7. Working with the owner

- **Recommend, don't list options.** Give a recommendation rather than a survey of options, and ask only when a decision is genuinely theirs.
- **Honest reporting.** Report failures with the actual output. Separate "verified" from "expected". Correct your own earlier claims explicitly when they turn out wrong.
- **Timestamps** in IST.
- **Long runs:** the owner likes live progress on long test runs (counts passed and failed so far).
- **Pre-existing failures:** keep them separate from failures caused by your change. Prove it by running the same test on `main` without your change.
