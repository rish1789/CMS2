# 041 — CI/CD Quality Gates

**Module:** Cross-Cutting / Tooling
**Status:** Ready for spec-kit intake

## User Story
As a maintainer of CMS2, I want dependency vulnerabilities and coverage gaps surfaced automatically on every push/PR, so that regressions and known-CVE dependencies are caught before merge instead of relying on manual discipline.

## Context
A CI workflow (`.github/workflows/ci.yml`) already exists and runs backend `spotlessCheck` + `test` and frontend `lint` (oxlint) + `tsc -b` + `test` on every push/PR to `main`. `PRODUCTION_ROADMAP.md` Phase 2 identified four additional zero-budget quality gates that were never added: Dependabot, an OWASP dependency-vulnerability scan for the JVM backend, an `npm audit` CI step for the frontend, and Jacoco coverage *reporting* (not gating — gating is deferred until 045 backfills real unit tests, since gating on coverage now would just fail on the Testcontainers/Docker sandbox limitation that blocks most of the backend's existing test suite from running at all in CI... actually CI runs on GitHub Actions with real Docker, not this sandbox, so Testcontainers tests DO run in CI — confirm this during planning).

Verified current gaps (2026-09-15 audit): no `.github/dependabot.yml`, no OWASP Dependency-Check plugin in `backend/build.gradle`, no `npm audit` step in the CI workflow, no Jacoco in `backend/build.gradle`.

## Business Rules
- Dependabot MUST be configured for both ecosystems present in this repo: `gradle` (backend) and `npm` (frontend), each with its own update schedule.
- The OWASP Dependency-Check Gradle plugin MUST fail the build only above a defined CVSS severity threshold (not on every low-severity finding, which would make the gate too noisy to be useful) — the specific threshold is a planning-time decision, default to a commonly-used threshold (e.g. CVSS >= 7, "High") unless research suggests otherwise.
- `npm audit` MUST run as its own CI step in the frontend job, but its failure mode (hard-fail vs report-only) needs a decision at planning time given the frontend's current dependency tree — check whether `npm audit` currently reports any existing high/critical findings before deciding whether to hard-gate immediately.
- Jacoco MUST be added for coverage *reporting* only in this feature (uploaded as a CI artifact or summary) — it MUST NOT gate the build on a coverage percentage yet, since 7 of 9 backend modules currently have no unit-test layer at all (see 045) and gating now would be gating on a number no one can yet move without that separate feature.
- None of these additions may change existing test behavior or break the currently-passing CI pipeline.

## Acceptance Criteria
- Given a pull request that bumps a dependency to a version with a known high/critical CVE, when CI runs, then the OWASP Dependency-Check step fails the build with a clear report of the vulnerable dependency.
- Given the current dependency tree with no such PR, when CI runs on a normal commit, then the OWASP Dependency-Check step passes (i.e., it isn't currently failing on pre-existing findings — if it does fail on existing findings, that's a real finding to report to the user, not silently suppress).
- Given `.github/dependabot.yml` after this feature, when GitHub processes it, then it opens update PRs for both the `gradle` and `npm` ecosystems on their configured schedule.
- Given a backend test run in CI after this feature, when it completes, then a Jacoco coverage report is generated and available as a build artifact, with no build failure caused by coverage percentage.
- Given a frontend CI run after this feature, when `npm audit` executes, then its result (pass/fail/report) is visible in the CI log, per whatever failure-mode decision was made at planning time.

## Dependencies
- Benefits from 040 (a working local dev environment makes it easier to test these changes locally before pushing) but does not hard-depend on it.
- Should precede 045 (backend unit-test backfill) only in the sense that Jacoco reporting from this feature gives 045 a way to measure its own progress — not a hard blocking dependency.

## Explicitly Out of Scope
- Coverage-percentage gating (deferred until real unit-test coverage exists via 045).
- Any change to existing test logic or test files.
- SAST/static-analysis tooling beyond dependency-vulnerability scanning (e.g. no CodeQL, no Snyk) — out of scope for this pass unless the user requests it later.

## Source References
- `PRODUCTION_ROADMAP.md` §1.4, §3 Phase 2, §4 (Free tooling to adopt)
- Verified against current repository state via direct inspection, 2026-09-15 (no dependabot.yml, no OWASP/Jacoco in build.gradle, no npm audit step in ci.yml)
