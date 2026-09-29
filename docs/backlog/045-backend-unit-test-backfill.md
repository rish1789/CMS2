# 045 — Backend Unit-Test Backfill

**Module:** Cross-Cutting / Backend Reliability
**Status:** Ready for spec-kit intake

## User Story
As a developer working on CMS2's business-critical modules, I want a fast, dependency-free (no Docker/Testcontainers) unit-test layer for the modules that currently only have integration tests, so that I get real regression feedback in seconds instead of needing a Docker-capable environment — and so this sandbox's confirmed Docker/Testcontainers limitation stops meaning "most of the backend's tests can't be verified here at all."

## Context
Verified current state (2026-09-15): 7 of 9 backend modules — `booking`, `scheduling`, `waitlist`, `clinical`, `discovery`, `notification`, `inbox` — have *only* an `integration/` test subfolder (Testcontainers-backed, real Postgres) and no `unit/` subfolder at all. Only `identity` and `patient` have all three shapes (unit/integration/contract) per `CONTRIBUTING.md`'s documented test-shape convention. Combined with this project's own confirmed sandbox limitation (memory: `docker_testcontainers_sandbox_limitation` — Docker/Testcontainers cannot run in this development sandbox, confirmed across all 39 backlog features), this means the majority of the backend's own business logic has no test layer that can be executed and verified in this environment at all — every prior feature's integration tests were written and confirmed to *compile* but never confirmed to *pass*.

This is a large, genuinely valuable, and long-running piece of work: writing real unit tests (pure Mockito, no Spring context, per `CONTRIBUTING.md`'s existing test-shape convention already used by `identity`/`patient`) for the actual existing, converged business logic in 7 modules. It does not change any application behavior — it only adds tests proving the *existing* behavior is correct, and gives this sandbox (and any fast local dev loop) a test layer it can actually run today.

## Business Rules
- New unit tests MUST follow the exact same shape as the existing `identity`/`patient` unit tests: pure Mockito (mocked repositories/collaborators), no Spring context (`@ExtendWith(MockitoExtension.class)`, not `@SpringBootTest`/`@DataJpaTest`), fast (milliseconds, not seconds).
- Coverage priority order (per `PRODUCTION_ROADMAP.md`'s own stated priority): `booking` and `scheduling` first (the two modules the roadmap calls "business-critical"), then `waitlist`, `clinical`, `discovery`, `notification`, `inbox` in any reasonable order — this should be broken into sub-phases at planning/task-generation time rather than attempted as one monolithic task, given the scale (booking alone has 66 files).
- Unit tests being added here MUST test genuinely existing, already-implemented, already-converged business logic — this feature does not change what any service does; if writing a test reveals an actual bug (as has happened repeatedly across this project's convergence history), that's a legitimate find to report and fix with the user's awareness, not something to silently paper over with a test that asserts the buggy behavior.
- This feature does NOT need to achieve 100% coverage of every class in every module — prioritize services with real conditional logic, race-closure guards, and business-rule branches (the classes convergence passes have historically found bugs in) over pure data-holder classes or trivial pass-through repositories.
- Existing integration tests in these 7 modules MUST remain untouched — this is additive, not a replacement of the existing (unexecuted-in-sandbox, but presumably correct) integration test suite.

## Acceptance Criteria
- Given the `booking` module after this feature, when `gradle test --tests "com.cms.booking.unit.*"` (or equivalent) is run in this sandbox with no Docker available, then the tests execute (not skip, not error on missing Docker) and pass.
- Given the same for `scheduling`, `waitlist`, `clinical`, `discovery`, `notification`, `inbox`, when each module's new unit tests are run, then they execute and pass in this sandbox without requiring Docker.
- Given the full backend test suite after this feature, when run in a real CI environment with Docker available (e.g. GitHub Actions), then both the new unit tests and all pre-existing integration tests pass with zero regressions.
- Given Jacoco coverage reporting (if 041 has landed by this point), when a coverage report is generated after this feature, then it shows a measurable increase in line/branch coverage for each of the 7 previously-unit-test-free modules.
- Given any genuine bug discovered while writing these tests, when found, then it is reported explicitly (not silently fixed-and-hidden) and fixed with a regression test proving the fix, following this project's established convergence-pass precedent.

## Dependencies
- Depends on all 39 existing converged features being stable, since this feature tests their already-implemented logic — no new application behavior is introduced here.
- Given the scale (7 modules), this feature will likely need to be split into multiple spec-kit passes (e.g., "045a: booking + scheduling unit tests" then "045b: remaining 5 modules") at planning time if a single pass proves too large for one implement/converge cycle — flag this explicitly during `/speckit-plan` rather than attempting a single unmanageable task list.

## Explicitly Out of Scope
- Rewriting or replacing any existing integration test.
- Achieving formalized 100% coverage — this is a pragmatic backfill of the highest-value gaps, not a coverage-percentage mandate (gating on a specific number is explicitly deferred, see 041).
- Any change to business logic itself, except where a genuine bug is found and must be fixed (see Business Rules above) — and any such fix is a reported finding, not a silent side effect.

## Source References
- `PRODUCTION_ROADMAP.md` §1.5, §3 Phase 5 item 5
- `CONTRIBUTING.md` (documented three-test-shape convention: unit / contract / integration)
- Project memory `docker_testcontainers_sandbox_limitation` (confirmed across all 39 backlog features)
- Verified against current repository state via direct inspection, 2026-09-15 (confirmed all 7 named modules have integration/ only, no unit/ subfolder)
