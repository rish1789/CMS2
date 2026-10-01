# CMS2: next phases, instructions and prompts

Prepared 2026-09-30 from local commit `a52f338` on `claude/067-queue-token-issuance-race`.

Updated 2026-09-30: the owner confirmed PRs #22 and #23 are merged. The local checkout still points to the feature branch above. Independent GitHub verification was unavailable because the CLI could not read its configuration; merge status below is owner-confirmed.

Updated 2026-10-01 using the supplied [live software audit](LIVE_SOFTWARE_AUDIT_2026-10-01.md). The next delivery sequence is **2A verification → 2R.1 patient outcomes/actions → 2R.2 login return → 2R.3 rate-limit errors → 2R.4 discovery paging → 2R.5 role-aware tools → 2B duplicate-phone handling → Phases 3–7**. Phase 2R is a focused repair wave, not the broader Phase 4 lifecycle redesign. The 2R labels are work-package labels, not reserved spec numbers.

This is an execution proposal, not authorization to implement every phase, merge PRs or deploy. Start with Phase 2A below. If local validation is blocked, prepare focused repair specs and tests while resolving the environment; do not mark the release baseline green. Run one work package at a time, review its evidence, and then proceed to the next. Phase 1 refers to the existing stabilization work in spec 065; the numbering below continues that programme.

## Status update — 2026-10-01 afternoon (IST), cloud session

Added after the plan was written. The original text below is preserved as written.

- **Phase 2A: closed.**
  - **Target commit:** `main` at `7b2550a`. It contains #22 and #23 and everything merged since (#24–#32).
  - **Backend:** full Docker-backed suite on that code, **1,113 passed, 0 failed, 0 skipped**, with `spotlessCheck` green. CI was green on the #32 head. Before that, `main` CI was green on `2919756` (#31) and `52f9854` (#30).
  - **Frontend gates on `7b2550a`:** `npm ci` succeeds and `npm run lint` passes (exit 0, 24 warnings, all pre-existing). `tsc -b` is clean. Vitest: **78 files, 445/445**. The build passes, with a bundle warning: 568.74 kB (gzip 131.32 kB) is over the 500 kB threshold. That is a Phase 6 item.
  - **Scheduled-job isolation:** done in #22. The sweeps are disabled in integration tests, and their dedicated tests remain.
  - **Specs 065–068:** convergence is done (#32). 065, 066 and 067 converged with no findings; 068's T033 and T034 are implemented.
  - **Open, owner-side environment issue:** the 1 October `processTestResources` AccessDeniedException on the Windows machine. Most likely OneDrive is locking files under `backend/build`. Fix it by building from a checkout outside OneDrive, or by pausing sync during builds. This blocks local validation, not the code.
  - **Still deferred:** the spec 067 historical orphan-token check on a real database.
- **Phase 3A, operational time zone (PB-005): decided and done.** The JVM is pinned to `Asia/Kolkata` (#29). Session-bookability guards come from 065.
- **Phase 3B, fee ownership (SEC-03): decided and done.** Fees are clinic-owned; see spec 068 (#30 and #32).
- **Numbering:** 068 is taken, so the next spec is **069**.
- **Phase 2R.1: done.** Spec 069 was merged in #34; live-audit findings 1–3 are fixed. Evidence:
  - full backend suite: 1,133 passed, 0 failed;
  - frontend: Vitest 457/457;
  - runtime UI verified with synthetic data.
- **Next package:** 2R.2 (login return).

## Current position

Use current source, Git history and recent spec results ahead of older roadmap prose:

- Spec 065 implemented fail-closed authentication, active-doctor checks for clinical creation, persistent session cancellation, shared availability checks and elapsed-slot rejection. Its progress entry says implemented, with no convergence pass recorded.
- Spec 066 fixed concurrent linking of one patient account at a clinic; its merge appears in local history.
- Local history also includes waitlist race fixes, concurrent booking rate-limit enforcement and dependency updates. Revalidate remaining audit items before treating them as open.
- Spec 067 implemented serialized queue-token issuance and atomic queue booking/token creation. **PRs #22 and #23 are merged, confirmed by the owner.** The older spec task entry describing #23 as unmerged is historical. No additional integration branch or merge is required for these PRs.
- The latest full backend run recorded in the inspected spec 067 artifacts is **1,067 tests: 1,066 passed, 1 failed, 0 skipped**. The documented failure is a scheduled no-show sweep changing a test fixture; PR #22 addresses it. Check the merged commit's CI evidence before scheduling another full run: a green combined baseline may already exist, but it was not verified in this planning session.
- The older audit's 199 failures and claims that no CI/Git repository exists are historical, not the current baseline. CI exists in `.github/workflows/ci.yml`.
- Git status showed a dirty nested checkout at `docs/.claude - Copy/worktrees/modest-tharp-3fd7e7`. Preserve it; it is unrelated to this plan.

No application tests were rerun for this planning-only change. Numbers above are recorded evidence, not a new certification.

### New evidence from the 1 October live audit

The supplied report records 78 frontend test files / 441 passing tests, a passing production build, lint with warnings and healthy backend responses. The build also reported a bundle over the 500 kB warning threshold; this is a measured follow-up for Phase 6, not a reason to delay the correctness repairs below.

The backend test run **did not complete**: `:processTestResources` hit `AccessDeniedException` for `backend/build/resources/test` after a Gradle cache access issue was resolved. This is an environment/validation blocker, not proof of failing backend tests. It neither supersedes the older completed test run nor certifies the merged baseline.

All seven reported findings are P2. Findings 1, 2, 4 and 7 were reproduced in the UI; finding 3's enabled action was observed but cancellation was not submitted; finding 5's missing CORS header was reproduced by a bounded API probe and its signup fallback gap was source-confirmed; finding 6 is a contract gap supported by paging API checks, not a demonstrated 21-result UI test. These are supplied audit observations, not a fresh browser audit in this planning session. Source spot-checks here corroborated the session-completion label, next-visit filter, unconditional login destination and rate-limit filter ordering.

The audit did not exercise booking/clinical writes, cross-clinic isolation or concurrent writes. Use disposable synthetic fixtures for the new regression tests; do not reuse a patient's real appointment to prove a write-path fix.

### Provisional quality assessment supplied on 1 October

| Area | Score | Assessment |
|---|---|---|
| Software quality | 6/10 | Build and 441 frontend tests pass, but important integration bugs remain. |
| Smooth operation | 6/10 | Main pages and logins work; lost navigation and misleading actions interrupt workflows. |
| UI | 7/10 | Consistent styling, readable screens and reasonably clear layouts. |
| UX | 5/10 | Conflicting appointment statuses undermine trust; users encounter actions they cannot complete. |

These are the supplied evaluator's provisional scores, not measured release gates or a new assessment performed here. The working prototype is not yet demonstrated ready for dependable daily clinic use. Fixing the seven findings could move it toward 8/10, but no score improvement is earned automatically by closing tickets or passing frontend tests.

**Primary acceptance gate: reliable patient information.** For the same synthetic appointment, compare the staff day sheet, patient dashboard, My bookings and booking detail after refresh and through the supported live-update behaviour. Labels may differ by audience but must agree on the underlying outcome. A no-show must never simultaneously be presented as a completed visit or the patient's next visit. Available actions must agree with server eligibility. Cover waiting/delayed, completed, no-show and cancelled outcomes across supported appointment modes. Capture the expected/actual result per screen and any stale-state interval.

Preserve the existing visual system during these repairs. Prioritize truthful state, successful navigation and valid actions before cosmetic redesign. The broader lifecycle work must preserve these acceptance cases.

**Retesting required before a daily-use readiness claim:** replay all seven findings on the combined repair commit, finish backend validation, and explicitly evaluate mobile layouts, keyboard/accessibility behaviour, concurrent bookings and end-to-end clinical workflows with synthetic data. Include tenant/role isolation and clinical immutability in the relevant tests. These areas were not fully covered by the supplied audit; do not mark them passed by inference. Phase 5C and Phase 6 carry the broader verification work, while 2R proves the focused repairs.

**Post-repair assessment prompt**

```text
Reassess CMS2 after the Phase 2R repairs using the original 1 October audit and
the provisional scorecard in docs/NEXT_PHASES_ACTION_PLAN.md. Identify the exact
combined commit and test environment. Replay all seven findings with synthetic
data and compare one appointment across staff day sheet, patient dashboard,
My bookings and detail for delayed/waiting, completed, no-show and cancelled
outcomes. Check action eligibility and refresh/live-update consistency.
Report each finding as fixed, still failing or unverified with evidence.
Record backend/frontend checks and explicitly report coverage or gaps for
mobile, accessibility, concurrent bookings and end-to-end clinical workflows.
Re-score the four areas only where evidence supports it; do not assume 8/10.
Separate readiness for a controlled pilot from dependable daily clinic use.
Do not deploy or modify real patient/clinical data during verification.
```

## Rules for every work package

1. Read `CLAUDE.md`, `.specify/memory/constitution.md`, the relevant current spec, and the audit entry. Read `PRODUCT.md` and `DESIGN.md` for UI work.
2. Inspect the branch, working tree and existing PRs before starting. Preserve unrelated changes. Choose the next unused spec number at execution time; do not assume 068 is still free.
3. New behaviour follows specify → clarify when needed → plan → tasks → analyze → implement → verify/review. Use the repository's spec-kit commands if available, or produce equivalent artifacts. Small fixes still require focused regression evidence.
4. Follow the constitution's test-first workflow for backend behaviour and migrations. Record the failing regression before the fix. Resolve any required test review under the authorization of that execution session.
5. Preserve clinic isolation, independent authentication realms, immutable clinical records, forward-only migrations and explicit cross-module events.
6. Give each work package one coherent PR when PR creation is requested. Report test totals, failures and skips honestly. A focused pass does not imply the full suite passed. Merge and deployment are separate actions.
7. Close a package only after acceptance criteria, relevant tests, migration review where applicable, and a review for regressions. Update audit status and spec/progress evidence with the commit and date.

### Reusable implementation preamble

Paste this before any implementation prompt below:

```text
Work in CMS2. Read CLAUDE.md and .specify/memory/constitution.md, then
docs/NEXT_PHASES_ACTION_PLAN.md. Inspect the current branch and preserve unrelated
changes, including nested checkouts. Revalidate the cited audit finding against
the current code before changing it; do not redo completed work.

Implement only the work package requested below. For new behaviour, complete
the spec, plan, tasks and consistency analysis before implementation. Follow
the constitution's test-first and review requirements. Keep tenant isolation,
immutable clinical records and existing API contracts unless this package
explicitly changes them. Record exact checks and limitations. Update the
relevant audit and progress entries. Finish with changes, evidence, remaining
risks and the next dependency. Do not merge or deploy.
```

## Phase 2 — Finish stabilization and establish a trustworthy baseline

### 2A. Record the merged baseline's verification evidence

**Purpose:** finish the existing stabilization wave before adding new behaviour.

**Instructions**

- PRs #22 and #23 are already merged. Identify the target-branch commit containing both and inspect its CI evidence. Prepare a checkout of that commit while preserving local work; do not create a redundant integration branch.
- Verify that test scheduling is isolated without disabling production jobs. Preserve dedicated tests of the jobs themselves.
- Reuse complete green CI evidence for the combined commit when available. If missing, incomplete or failing, run the ordinary Docker-backed backend suite without an uncommitted init script and the frontend quality gates. Classify failures as product defects, harness defects or environment blockers.
- Resolve the 1 October `processTestResources` access blocker by checking permissions, file locks and the OneDrive-managed build location. Prefer a disposable checkout/build location if necessary. Do not delete arbitrary directories or terminate unrelated processes. Preserve application data and local work. A green pre-change baseline still needs appropriate verification after each repair.
- Reconcile specs 065–067 and current audit status. Preserve dated historical evidence rather than rewriting old runs as if they were green.
- Keep the historical orphan-token check from spec 067 as an explicit owner follow-up. That spec deferred running it on a real database; do not silently turn baseline verification into data repair.

**Done when:** the intended combined code has a full green backend run with Docker, frontend gates pass, CI evidence is captured, and remaining product decisions are listed separately. If an environment blocks execution, the gate remains unverified.

**Prompt**

```text
Execute Phase 2A of docs/NEXT_PHASES_ACTION_PLAN.md. PRs #22 and #23 are already
merged. Identify the target-branch commit containing both and collect its CI
results. Preserve local changes when preparing the merged checkout. Reuse
complete passing checks; run any missing or failed baseline gates, including
the normal full backend suite with Docker and frontend lint, type-check,
tests and build. Investigate scheduled no-show test interference only if it
still reproduces with #22 included. Record exact evidence and remaining
blockers, and reconcile specs 065-067 and the audit. Address the October 1
processTestResources AccessDeniedException as an environment blocker, not a test
assertion failure. If all required evidence is green, close 2A and identify
2R.1 as next. Do not create a redundant integration
branch, merge PRs, or run production data checks or repairs.
```

### 2R. Focused repairs from the live audit

**Scope:** repair observable behaviour using the current domain model and existing authorization rules. Do not wait for a new visit entity, global HTTP consolidation or a redesigned dashboard. Each package gets a focused spec/contract where behaviour changes, regression tests and a reviewable change.

| Package | Audit findings | Outcome | Later-phase relationship |
|---|---|---|---|
| 2R.1 | 1, 2, 3 | Correct patient visit outcome, next-visit selection and cancellation eligibility | Supplies regression coverage for Phase 4; does not redesign state ownership |
| 2R.2 | 4 | Login/signup returns to the selected clinic and doctor | Reused by Phase 5A session-expiry handling |
| 2R.3 | 5 plus signup fallback | Allowed browser clients can read 429 errors and see recovery guidance | Separate from Phase 3C limiter storage/proxy decisions |
| 2R.4 | 6 | Every matching discovery result is reachable | Completes a current API integration gap |
| 2R.5 | 7 | Dashboard and direct routes respect clinic roles | Reuses current server rules; no authorization-policy expansion |

#### 2R.1 — Patient outcomes and valid actions first

**Instructions**

- Keep booking cancellation state, the patient's own visit outcome and whole-session progress distinct. A completed session cannot establish that this patient attended. Do not rewrite booking or slot records merely to fix presentation.
- Expose the patient's visit outcome in the appropriate booking list/detail contracts. Use it consistently in My bookings, booking detail and the next-visit card. Reserve “Visit complete” for this patient's completed visit; show a no-show accurately even when the session has finished.
- Specify next-visit selection for future appointments, unresolved delayed appointments today, queue/untimed entries and terminal outcomes. Do not hide a still-waiting delayed patient merely because scheduled time passed; do not infer completion from elapsed time. Resolve any undocumented behaviour in a small decision table before implementation.
- Expose server-derived cancellation eligibility and a stable reason in booking details, reusing the actual cancellation policy. Cover the two-hour cutoff, disallowed slot states and existing queue restriction. Explain an unavailable action. The mutation endpoint must recheck eligibility because a displayed decision can become stale.
- Test authenticated ownership as contracts expand. Do not leak another patient's appointment through summary/detail/live-status endpoints.

**Acceptance:** no-show, completed and cancelled appointments cannot be presented as an upcoming visit; no-show is never labelled “Visit complete”; eligible delayed visits follow the agreed rule; list/detail/dashboard agree; unavailable cancellation has a clear reason; stale eligibility still receives the correct backend refusal.

**Prompt**

```text
Execute Phase 2R.1 in docs/NEXT_PHASES_ACTION_PLAN.md using findings 1-3 of
docs/LIVE_SOFTWARE_AUDIT_2026-10-01.md. Trace patient booking summaries/details,
PatientSessionLiveStatusController, NextAppointmentCard and cancellation policy.
Write a decision table separating booking state, own visit outcome and session
progress. Specify additive response fields and server-derived cancellation
eligibility/reason; reuse existing rules and resolve only genuinely undefined
delayed/queue next-visit semantics. Implement test-first using a fixed clock and
synthetic appointments. Cover no-show, completed, cancelled, future, delayed,
queue/untimed, cutoff boundaries, stale UI decisions and patient ownership.
Update My bookings, detail and dashboard consistently. Verify the reported
no-show scenario through the UI without changing real patient records. Do not
introduce a new visit entity or change cancellation policy as part of this fix.
```

#### 2R.2 — Preserve discovery intent through authentication

**Instructions:** consume the guard's original internal destination after login; carry it through signup and back to login where applicable. Preserve pathname, query string (especially `doctorId`) and relevant hash. Validate destinations against permitted patient/public routes, reject external/protocol-relative or malformed values, prevent authentication loops, and default to `/patient` when no usable destination exists.

**Acceptance:** discovery → login → selected clinic/doctor works; discovery → signup/authentication → selected clinic/doctor works; direct login keeps the normal dashboard destination; unsafe destinations do not redirect outside the application or into an authentication loop.

**Prompt**

```text
Execute Phase 2R.2 for live-audit finding 4. Reuse state.from from the route
guard and preserve a validated internal destination through patient login and
signup. Keep clinic and doctorId query context and use /patient as the fallback.
Add route-level tests for login, signup, direct entry, malformed/external targets
and redirect loops. Verify the discovery-to-authentication-to-booking journey.
Keep this focused; do not migrate all HTTP clients or authentication realms.
```

#### 2R.3 — Make throttling errors readable and actionable

**Instructions:** arrange CORS processing before any limiter short-circuit using the existing origin policy. Preserve throttling and unauthorized-origin restrictions; do not solve it with permissive wildcard CORS. Expose `Retry-After` if the client consumes it. Handle `RATE_LIMIT_EXCEEDED` and unknown parsed API errors in signup, with a visible fallback for network failures. Reuse established error UI; do not increase limits to make tests pass.

**Acceptance:** an allowed-origin request receives a readable 429 body and retry guidance; required exposed headers are readable; disallowed origins remain disallowed; OPTIONS/preflight still works; existing public throttled endpoints remain protected; signup shows an error for known, unknown and network failures. Test the real filter ordering, not just the limiter in isolation.

**Prompt**

```text
Execute Phase 2R.3 for live-audit finding 5 and the related SignupForm fallback
gap. Add failing HTTP integration coverage for a throttled allowed-origin request
through the actual CORS/security/limiter chain. Correct ordering without widening
allowed origins or weakening rate limits. Cover preflight, disallowed origins,
429 response headers/body and the public endpoints using this configuration.
Handle RATE_LIMIT_EXCEEDED, unknown API errors and network failures visibly in
signup; expose/read Retry-After only if needed by the chosen UI. Use an isolated
test limiter/window rather than exhausting a shared live environment's quota.
```

#### 2R.4 — Finish discovery pagination

**Instructions:** inspect the existing response before adding fields; reuse total/hasNext metadata if available. Send page/size, provide accessible paging controls and a deterministic secondary sort by a unique result key. Reset page on filter changes and prevent stale requests from overwriting newer results. Keep the existing size cap.

**Acceptance:** with more than 20 synthetic results, all matches are reachable; duplicate names do not cause omissions/duplicates across pages in an unchanged dataset; filters persist; changing filters resets the page; loading, failure/retry, empty and final-page states behave correctly. Do not claim snapshot consistency under concurrent dataset changes unless implemented.

**Prompt**

```text
Execute Phase 2R.4 for live-audit finding 6. Complete frontend use of the existing
discovery pagination contract, adding metadata only if missing. Ensure stable
ordering with a unique tie-breaker. Add page controls, filter/page coordination
and stale-request protection. Test more than 20 synthetic results including
duplicate names, first/last/empty pages, filter changes and retry. Verify all
results can be browsed with keyboard-accessible controls. Do not fabricate a
large live dataset or treat the existing three-result dataset as sufficient QA.
```

#### 2R.5 — Align tools and page access with clinic roles

**Instructions:** use the active clinic's role information consistently across sidebar, dashboard tiles and page/route entry points. Cover Onboard staff and Booking protection, then check equivalent links against existing rules. Define loading/denied states so a restricted editable form does not flash before roles resolve. Keep backend authorization as the enforcement boundary.

**Acceptance:** Doctor and Operations accounts cannot reach administrator-only editable forms via tiles or direct URLs; active ClinicAdmins retain access; multi-role users and clinic switching use the current clinic's permissions; backend refusal tests remain intact. This corrects a UI mismatch, not a proven backend privilege bypass.

**Prompt**

```text
Execute Phase 2R.5 for live-audit finding 7. Align ClinicToolsDashboard and
ClinicToolPages with existing active-clinic role rules for Onboard staff and
Booking protection. Reuse existing role helpers where suitable. Test ClinicAdmin,
Doctor, Operations, multiple roles, loading/denied state, clinic switching and
direct URLs. Ensure unauthorized users never receive an editable restricted
form while preserving server authorization. Verify role-specific navigation
using synthetic accounts; do not submit real staff onboarding requests.
```

**Repair-wave exit:** all seven findings have traceable regression evidence and browser verification where relevant; full backend and frontend gates pass on the combined repair commit (or remain explicitly unverified if blocked). Update finding statuses with commit and evidence; preserve the original audit observations. Proceed to 2B, then the broader phases without reimplementing these repairs.

### 2B. Fix staff-side duplicate-patient phone handling

**References:** A-05, PB-001, PB-002. Spec 066's same-account linking fix does not by itself resolve this different staff-created-patient path.

**Order:** after the 2R live-audit repairs. This remains important but was not exercised by the supplied read-oriented audit and has not been newly reproduced here.

**Instructions:** reproduce collisions in staff fixed-time booking, staff queue booking and front-desk walk-in; agree an explicit conflict response; reuse an existing appropriate patient-resolution seam where possible; preserve database uniqueness and transaction correctness. Do not recover by querying again inside an already-aborted transaction. Do not silently merge patient identities based only on a phone number.

**Done when:** same-clinic collisions produce the agreed response rather than an incorrect slot error or 500; concurrent attempts preserve uniqueness; other clinics remain independent; failed attempts leave no booking or token artifacts; the UI preserves entered values and offers a clear recovery action.

**Prompt**

```text
Execute Phase 2B: resolve PB-001 and PB-002 across staff fixed-time booking,
staff queue booking and front-desk walk-in. First reproduce each current failure
with database-backed tests, including concurrent requests. Specify a stable
duplicate-phone conflict contract and safe user recovery; do not silently link
or merge identities. Implement the smallest shared solution consistent with
the existing patient uniqueness rules. Test rollback, no orphan tokens,
cross-clinic independence and the frontend error handling. Preserve 066/067's
locking and transaction guarantees.
```

## Phase 3 — Decide and enforce time and access rules

**Dependency:** Phase 2A. Ship separate packages; do not combine time semantics, pricing ownership and authentication into one PR.

| Package | Scope | Decision needed before implementation |
|---|---|---|
| 3A | A-07/PB-005; remaining A-08/PB-004 | Single India operational zone versus per-clinic zones; ended-session booking rules by booking path |
| 3B | B-03/SEC-03 | Whether doctor fees and appointment types are global or clinic-owned |
| 3C | B-07; B-05/B-06 if still open | Account deactivation semantics, token invalidation expectations, enumeration trade-off and deployment/proxy assumptions |
| 3D | PB-009 and waitlist eligibility | Whether loss of clinic/doctor verification blocks new bookings, or only discovery; restoration behaviour |

**Suggested decisions for discussion:** retain a single explicit `Asia/Kolkata` operational zone while the product serves Indian clinics; allow late walk-ins only where the confirmed workflow permits them; prefer clinic-owned pricing if clinics must operate independently. These are proposals, not approved requirements. Do not reject all post-end-time walk-ins: spec 063 explicitly permits that use case today.

**Decision prompt — use before the implementation prompts**

```text
Prepare a concise decision record for Phase 3 of docs/NEXT_PHASES_ACTION_PLAN.md.
Trace current behaviour and contracts for operational time zone, ended-session
booking, fee/type ownership, account deactivation and de-verification. For each,
show two viable choices, the user-visible consequences, migration impact and
your recommendation. Ask only the product choices needed to settle the rules.
Do not implement an unresolved choice or present a recommendation as approval.
```

**3A implementation prompt**

```text
Implement Phase 3A using the approved decision record. Apply an explicit,
testable operational clock/zone to booking, cancellation cutoffs, no-show,
completion, waitlist expiry, session generation and live status as applicable.
Keep date-only and instant semantics distinct; do not blindly rewrite timestamps.
Complete remaining session-bookability guards by path without removing approved
late walk-ins. Test midnight boundaries and operation on a UTC-configured host,
and ensure browser display agrees with the server's operational day.
```

**3B–3D implementation prompt — substitute one package only**

```text
Implement Phase [3B / 3C / 3D] using its approved decision record. First list
affected endpoints, data ownership, existing contracts and migration needs.
Add positive, negative, cross-clinic and stale-session tests as relevant.
Implement only this decision. If existing records require transformation,
provide a forward-only migration and explicit conflict handling rather than
guessing ownership. Verify the corresponding staff, patient and admin flows.
```

**Done when:** approved rules have traceable tests and contracts; authorization covers direct API requests, not only UI visibility; migration and restoration semantics are explicit where needed.

## Phase 4 — Make the visit lifecycle consistent

**References:** C-02, A-09/PB-007, remaining B-04/PB-008, E-01/E-03/E-04, K-03. **Dependency:** baseline and relevant Phase 3 decisions.

Carry forward 2R.1's outcome/eligibility contracts and regression cases. The urgent patient-facing corrections ship in 2R.1; this phase addresses deeper lifecycle ownership and transitions only where still needed.

**Instructions**

1. Define a transition table covering scheduled, waiting, checked-in, with-doctor, completed, no-show and cancelled outcomes as appropriate. Choose the owner of visit state before proposing a new entity or moving columns.
2. Decide whether check-in and consultation are separate, which actors may transition each state, whether manual no-show/undo exists, when clinical creation is legal, and what cancellation means after arrival.
3. Reconcile fixed-time, queue and untimed walk-in behaviour, background completion, patient live status, retention eligibility and verification cascades.
4. Split delivery into domain/schema changes, service/API enforcement, then staff/patient UX. Avoid a single broad rewrite.
5. Build a combined consultation workspace only after the lifecycle and document-creation rules are stable. Preserve write-once notes and prescriptions.

**Design prompt**

```text
Design Phase 4 of docs/NEXT_PHASES_ACTION_PLAN.md; do not implement yet.
Produce the current and proposed visit transition tables with actor, precondition,
side effects, timestamps and API outcomes. Cover fixed-time, queue and walk-in,
cancellation after arrival, no-show, auto-complete and clinical document creation.
Compare retaining current entities with introducing an explicit visit entity.
Recommend the smallest model that satisfies confirmed workflows. Identify
product decisions, data migration ambiguities and compatibility requirements.
Break implementation into independently reviewable packages.
```

**Implementation prompt — repeat for one approved package at a time**

```text
Implement the next approved Phase 4 package. Use the signed-off transition table
as the source of truth. Test valid and invalid transitions, concurrent actions,
tenant/role checks, clinical creation eligibility and immutable documents.
Verify effects on queue position, live status, cancellation, anonymization and
scheduled jobs. For schema changes, test migration of legacy records and refuse
to invent missing clinical history. Keep UI vocabulary consistent with the
approved lifecycle. Report compatibility and rollout implications.
```

**Done when:** one documented lifecycle explains every visit mode; invalid transitions are refused by the backend; cancellation cannot accidentally reopen an attended visit; document creation follows approved eligibility rules; staff and patients see consistent state.

## Phase 5 — Consolidate API and workflow UX

**References:** D-01–D-03, E-05–E-07, G-01/G-02, H-05/H-06, J-01/J-02. **Dependency:** stable contracts for each area being migrated. Frontend client work can start after Phase 2A without waiting for all of Phase 4.

Preserve and reuse 2R.2's safe return-path behaviour, 2R.3's visible errors, 2R.4's pagination and 2R.5's role checks. These repairs do not depend on completing this broader consolidation.

**Instructions:** deliver three packages: (5A) shared frontend HTTP and realm-aware expiry; (5B) authorization/error/validation consistency one module at a time; (5C) critical-flow UX, runtime accessibility and a small E2E suite. Preserve API semantics during mechanical consolidation. Make any contract change explicit.

**5A prompt**

```text
Execute Phase 5A. Inventory current raw fetch calls and shared API-client users.
Migrate incrementally to the shared client without losing typed errors, headers,
abort handling or intentional unauthenticated requests. Add session-expiry
handling that clears only the affected authentication realm, preserves a safe
internal return path and avoids redirect loops. Keep login failures and 403
authorization refusals distinct from expired sessions. Test all three realms,
concurrent 401s and representative successful/error requests.
```

**5B prompt**

```text
Execute Phase 5B for one module selected by actual duplication and risk.
Consolidate clinic authorization and exception mapping while preserving existing
status codes and tenant checks. Add missing request validation only with explicit
contract coverage. Document module dependency direction and add focused checks
where they protect a real boundary. Do not flatten the domain modules or turn
this package into a repository-wide rewrite.
```

**5C prompt**

```text
Execute Phase 5C against the approved lifecycle and DESIGN.md. Verify staff
booking, front-desk walk-in, day sheet, consultation and patient status flows.
Fix actual route/modal, error-recovery, keyboard, focus and non-color status
problems found at runtime. Add a small real-backend E2E suite covering walk-in
registration through completion, patient booking/cancellation and an access
refusal. Use synthetic data and deterministic time. Record desktop/mobile and
keyboard verification evidence, including any unverified screen-reader checks.
```

**Done when:** migrated callers preserve behaviour; realm expiry and access refusal work correctly; core cross-role flows pass; accessibility claims have runtime evidence.

## Phase 6 — Prepare a measurable pilot release

**References:** F-01–F-04, C-03/C-04/C-07/C-08, B-09, I-02/I-03. **Dependency:** stable lifecycle and a green integration baseline.

**Instructions:** confirm the deployment topology first; measure expensive paths with representative synthetic data; bound scheduled scans and add indexes based on query evidence; test proposed constraints against existing data. Document single-instance limitations if that is the chosen pilot topology. Add distributed coordination only if multiple instances are actually required.

**Engineering prompt**

```text
Prepare Phase 6 engineering changes for the confirmed pilot topology. Measure
scheduled-job queries and major list endpoints before optimizing. Use query
plans and bounded fixtures to justify indexes, query windows and selective fetch
changes. Review persisted-state constraints and clinical immutability protections
against the finalized model. Verify production configuration, health exposure,
dependency-scan execution and sensitive-data logging. Keep deployment changes
reviewable and separate from domain changes; do not deploy.
```

**Release-readiness prompt**

```text
Prepare a pilot release dossier for CMS2, without deploying. Record the exact
candidate commit, CI results, migrations, configuration requirements, known
limitations, operator smoke steps and rollback/forward-fix strategy. Rehearse
database backup/restore and migration on disposable data where infrastructure
is available; mark unavailable rehearsals as unverified. Write concise staff
and operator guides for the actual implemented workflows. Report a go/no-go
recommendation supported by evidence and list only concrete remaining blockers.
```

**Done when:** the release candidate is identified, CI and cross-role smoke tests pass, backup/restore evidence exists, configuration is documented, migrations have a recovery plan and the operator can follow the runbook. Deployment remains a separate requested action.

## Phase 7 — Select new capabilities after stabilization

These are options, not a commitment to build all of them. Select based on pilot feedback:

1. Doctor leave/holiday exceptions and ad-hoc availability (K-02).
2. Standalone patient registration with safe deduplication (K-08; depends on 2B).
3. Operational/clinical audit trail (K-05); move its minimum necessary scope earlier if a Phase 3/4 decision requires it.
4. Patient recovery and notification preferences/delivery (K-04/K-01), after deciding the recovery channel and delivery scope.
5. Atomic reschedule or payments only after an explicit documented scope decision.

Live notification delivery, payments, file uploads, a global medical record and atomic reschedule remain outside the constitution's current scope. A feature proposal is not an amendment.

**Prompt**

```text
Use pilot feedback and the current audit to propose the next single capability
from Phase 7. Describe the user problem, smallest useful scope, acceptance
scenarios, dependencies and operational cost. Identify any constitution boundary
and obtain an explicit documented product decision before crossing it. Produce
a spec and implementation plan for the selected capability; do not implement
unselected features or silently add infrastructure/providers.
```

## Verification commands and reporting

Use the checked-in wrappers and actual environment. On native Windows:

```powershell
# From backend/; Java 21 and Docker available for integration tests
.\gradlew.bat spotlessCheck
.\gradlew.bat test

# From frontend/; install from the lockfile if dependencies need preparing
npm ci
npm run lint
npx tsc -b
npm run test
npm run build
```

On Linux, use `./gradlew`. Start with relevant tests while developing; run full gates on the final candidate. Include the dependency checks configured in CI and distinguish executed scans from skipped scans. Never record a skipped scan as clean.

Every package's handoff should contain:

- Exact branch/commit and PR if one was created.
- Audit/spec IDs addressed and acceptance criteria satisfied.
- Tests/checks actually run, with passed/failed/skipped counts where applicable.
- UI verification and migration evidence where relevant.
- Unresolved decisions, risks and the next eligible work package.

## Source documents

- [Live software audit — 1 October 2026](LIVE_SOFTWARE_AUDIT_2026-10-01.md) — supplied feedback; findings 1–7 mapped to 2R.1–2R.5 above.
- [Current audit backlog](product-audit/10-PRODUCT-IMPROVEMENT-BACKLOG.md)
- [Defect register](product-audit/07-BUG-AND-DEFECT-REGISTER.md)
- [Testing audit](product-audit/09-TESTING-AND-QUALITY.md)
- [Spec 067 results](../specs/067-queue-token-issuance-race/tasks.md)
- [Progress ledger](../backlog/progress.md)
- [Constitution](../.specify/memory/constitution.md)

Treat `PRODUCTION_ROADMAP.md` and `HANDOFF.md` as historical context until their stale sections are reconciled. Root `specs/` and `backlog/` contain the active artifacts referenced by `CLAUDE.md`; do not independently edit duplicate `docs/specs/` or `docs/backlog/` copies without first deciding their ownership.
