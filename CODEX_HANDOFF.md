# Handoff for Codex — CMS2 (as of 2026-09-29, `main` `8e7a224`)

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
- **Numbering:** sequential. The next feature is **067**, because `specs/` ends at `066-patient-linking-race`.
- **Active feature:** write `.specify/feature.json` as `{"feature_directory": "specs/NNN-slug"}`.
- **Scripts:** the `.specify/scripts/powershell/*.ps1` helpers need `pwsh`, which may not be installed. Doing their steps by hand is fine.
- **Worked example:** `specs/066-patient-linking-race/`. It has a spec, a plan, research with rejected alternatives, and tasks with observed test results.

## 4. Commands

```bash
# Backend (from backend/). Integration tests use Testcontainers, so Docker must be running.
./gradlew spotlessCheck -x spotlessApply
./gradlew test -x spotlessApply --tests "*SomeTest"          # one class
./gradlew test --rerun -x spotlessApply --tests "*SomeTest"  # force a real re-run (bypass the Gradle cache)
./gradlew test -x spotlessApply                              # full suite: ~1,050 tests, ~22 min

# Frontend (from frontend/)
npm ci && npx tsc -b && npm run lint && npx vitest run       # 441 tests
```

**Docker in a Linux sandbox:** start the daemon with `DOCKER_MIN_API_VERSION=1.24 dockerd &`, because Testcontainers 1.21's docker-java is too old for Docker 29's default minimum API. Never `pkill -f` a broad pattern; it can kill the daemon or your own shell. Kill by PID.

**Test shapes** (`CONTRIBUTING.md`):
- pure Mockito unit tests;
- `@WebMvcTest` contract tests;
- `integration/` classes that extend an `Abstract*IntegrationTest` base. Each base has a Postgres `@Container` and `@DirtiesContext(AFTER_CLASS)`. A subclass `@AfterEach` runs **before** the base cleanup, so use it to delete child rows first.

## 5. Current state

- **Merged today:** PRs #11, #12, #13, #16, #15, #17 and #18. PR #19 (docs only: audit doc 07, `HANDOFF.md` Part 14 and this file) may still be open.
- **Expected backend failures on `main`,** based on per-branch runs. There has been no full run on `8e7a224` itself.
  1. `BookingRateLimitConcurrencyTest`: a **real bug**. A burst of simultaneous attempts exceeds the rate limit's race margin (12 against 9). The code is in `backend/src/main/java/com/cms/booking/service/BookingProtectionService.java` (`checkRateLimit`: count-then-insert with no lock).
  2. `PatientWaitlistJoinTest.rejectsADoctorNotStaffedAtTheClinic`: **blocked on an owner decision**, 404 or 409. The code maps `DOCTOR_NOT_STAFFED_AT_CLINIC` to 409. Don't change either side without the owner's answer.
- **Intermittent:**
  - `PartialSessionCancellationRangeTest.toTimeLeavesSlotsAtOrAfterTheUpperBoundUntouched`: seen once, passes alone; not investigated.
  - PB-003 `QueueSlotIssuanceConcurrencyTest` (concurrent queue-token issuance): not seen in recent runs.
- **Time-of-day trap:** many integration fixtures generate *today's* 09:00–13:00 session. An assertion that needs a still-bookable slot fails for any run after 09:00, because `SessionAvailabilityService` (065) refuses elapsed slots. That was the whole cause of the `ClinicDeVerificationCascadeTest` "bug". Use a future-dated session (for example `saveFixedTimeSessionWithSlotsOn(..., LocalDate.now().plusDays(1))` in `AbstractSessionCancellationIntegrationTest`).
- **CI:** `.github/workflows/ci.yml` has no `timeout-minutes`, so a hung backend job holds the runner for 6 h. CI on `main` after today's merges has not been checked.

## 6. Suggested next work, in priority order

1. Run the full backend suite on `main` to confirm the baseline in §5, then check GitHub Actions for `main`.
2. **The booking rate-limit margin** (a real bug). Check `specs/060-booking-abuse-prevention` for the specified margin and whether this restores specified behaviour (a small fix) or needs a new spec. Close it at the data layer, for example with the same account row lock the booking-limit check takes.
3. Investigate `PartialSessionCancellationRangeTest`. Suspect the time-of-day fixtures first.
4. PB-003, queue-token issuance under concurrency (see `docs/product-audit/07-BUG-AND-DEFECT-REGISTER.md`).
5. Housekeeping:
   - add `timeout-minutes: 45` to the CI backend job;
   - remove the stray gitlink `docs/.claude - Copy/worktrees/modest-tharp-3fd7e7`;
   - upgrade `actions/*@v4` to v5.
6. Run a converge pass for 065 and 066, following `.claude/skills/speckit-converge/SKILL.md`.
7. **Waiting on the owner, don't start:** the 404/409 decision, SEC-03 (per-clinic pricing) and PB-005 (time zones). The Dependabot PRs #1–#10 are unreviewed; some are major-version bumps (Spring Boot 4, TypeScript 7, Vitest 5, react-router 7), so don't merge them blindly.

## 7. Working with the owner

- **Recommend, don't list options.** Give a recommendation rather than a survey of options, and ask only when a decision is genuinely theirs.
- **Honest reporting.** Report failures with the actual output. Separate "verified" from "expected". Correct your own earlier claims explicitly when they turn out wrong.
- **Timestamps** in IST.
- **Long runs:** the owner likes live progress on long test runs (counts passed and failed so far).
- **Pre-existing failures:** keep them separate from failures caused by your change. Prove it by running the same test on `main` without your change.
