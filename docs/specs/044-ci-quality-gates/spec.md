# Feature Specification: CI/CD Quality Gates

**Feature Branch**: `044-ci-quality-gates`

**Created**: 2026-09-15

**Status**: Draft

**Input**: User description: "backlog/041-ci-cd-quality-gates.md" — add Dependabot, an OWASP dependency-vulnerability scan for the backend, an `npm audit` CI step for the frontend, and Jacoco coverage reporting (not gating) to the existing `.github/workflows/ci.yml`. Second of the 040-052 production-hardening/redesign wave.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Automatic dependency update PRs (Priority: P2)

A maintainer wants outdated or vulnerable dependencies surfaced automatically instead of discovered by accident, for both the Gradle backend and npm frontend.

**Why this priority**: Passive, zero-maintenance-cost safety net; not urgent but the cheapest of the four gates to add.

**Independent Test**: Merge the Dependabot config and confirm GitHub's Dependabot tab shows it scanning both ecosystems on schedule.

**Acceptance Scenarios**:

1. **Given** `.github/dependabot.yml` is present, **When** GitHub processes it, **Then** it opens update PRs for outdated `gradle` and `npm` dependencies on the configured schedule.

---

### User Story 2 - Backend dependency vulnerability scanning blocks bad merges (Priority: P1)

A maintainer wants a pull request that introduces a dependency with a known high/critical CVE to fail CI, so the vulnerability never reaches `main`.

**Why this priority**: The one gate in this feature that actually blocks a merge — the highest-value, highest-risk addition, so it goes first.

**Independent Test**: Point a test branch at a JVM dependency version with a known high-severity CVE and confirm the backend CI job fails with a clear report; confirm a normal commit with today's dependency set passes.

**Acceptance Scenarios**:

1. **Given** a dependency with a CVSS ≥ 7 ("High") known vulnerability, **When** CI runs, **Then** the backend job fails with a report naming the vulnerable dependency and CVE.
2. **Given** the current, unmodified dependency set, **When** CI runs, **Then** the new scan step passes (does not fail on pre-existing findings, if any exist below the threshold).

---

### User Story 3 - Frontend dependency audit visibility (Priority: P3)

A maintainer wants frontend dependency vulnerabilities visible in CI output without CI breaking on a moderate-severity finding that has no clean fix available today.

**Why this priority**: Lower priority than the backend gate because a real, currently-unresolved moderate finding exists today (see Assumptions) — a naive hard-fail-on-any-finding policy would break CI on landing, which is worse than not having the check at all.

**Independent Test**: Run `npm audit` in CI on the current dependency tree and confirm its output (including the known existing moderate finding) appears in the CI log without failing the build; confirm a newly introduced high/critical finding does fail it.

**Acceptance Scenarios**:

1. **Given** the current frontend dependency tree (which has a known moderate-severity finding as of 2026-09-15 with no non-breaking fix available), **When** CI runs `npm audit`, **Then** the step reports the finding visibly but does not fail the build.
2. **Given** a newly introduced high or critical severity frontend vulnerability, **When** CI runs, **Then** the frontend job fails.

---

### User Story 4 - Coverage visibility without a false gate (Priority: P3)

A maintainer wants to see backend test coverage trend over time without CI failing on a coverage percentage that can't currently be improved everywhere (most backend modules have no unit-test layer yet).

**Why this priority**: Purely observational — no gate, so no urgency; deferred until 045 (backend unit-test backfill) gives it something meaningful to measure.

**Independent Test**: Run backend CI and confirm a Jacoco coverage report artifact is produced, with no build failure tied to its percentage.

**Acceptance Scenarios**:

1. **Given** a backend CI run, **When** it completes, **Then** a Jacoco coverage report is available as a build artifact, and the build's pass/fail status is unaffected by the coverage number.

---

### Edge Cases

- What happens when Dependabot opens a PR for a major-version bump that would break the build? That PR is expected to fail CI (the existing test suite is the safety net) — this feature does not need to auto-merge or auto-validate Dependabot PRs beyond what the existing CI pipeline already does for any PR.
- What happens if the OWASP Dependency-Check plugin's vulnerability database is unreachable during a CI run (offline/rate-limited)? This is a known operational risk of that tool; the plan should note a reasonable retry/cache behavior rather than leaving the build permanently blocked by a transient network failure, without over-engineering a solution not requested.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The repository MUST have a `.github/dependabot.yml` covering both the `gradle` and `npm` package ecosystems.
- **FR-002**: The backend CI job MUST run a dependency-vulnerability scan that fails the build when a dependency has a known vulnerability at or above a defined severity threshold (CVSS ≥ 7 / "High").
- **FR-003**: The frontend CI job MUST run `npm audit` and surface its output, without failing the build on the currently-known moderate-severity finding; it MUST fail on a high/critical severity finding.
- **FR-004**: The backend CI job MUST produce a code coverage report as a build artifact; this report MUST NOT affect build pass/fail status.
- **FR-005**: None of these additions may change any existing test's behavior or cause a currently-passing CI pipeline to fail on unrelated grounds.

### Key Entities

N/A — no data model involved.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A pull request introducing a backend dependency with a CVSS ≥ 7 vulnerability fails CI automatically, with zero manual review needed to catch it.
- **SC-002**: Dependabot opens at least one update PR within its first scheduled scan window for each of the two ecosystems (verified once merged and live on GitHub — cannot be verified in this local/CI-less sandbox).
- **SC-003**: Frontend CI visibly reports the current known moderate finding without blocking merges, and would fail on a newly introduced high/critical one.
- **SC-004**: A backend coverage report is produced on every CI run with zero coverage-driven build failures.

## Assumptions

- As of 2026-09-15, the frontend has one real, existing moderate-severity finding (`react-router` / `react-router-dom`, CVE-2025-68470-adjacent, GHSA-wrjc-x8rr-h8h6 and GHSA-337j-9hxr-rhxg) whose only available fix (`npm audit fix --force`) is a breaking major-version upgrade — fixing it is explicitly out of scope for this feature (a CI-tooling addition, not a dependency-upgrade feature); the audit gate is designed around its existence rather than being silently broken by it on day one.
- The OWASP Dependency-Check CVSS threshold of 7 ("High") is a reasonable, commonly-used default per industry practice, chosen because no current backend dependency is known to trigger it. **Confirmed during implementation**: the scan requires a free NVD API key to run at all (the NVD has required one for reliable access since Feb 2023 — without it the scan fails outright, not just slowly); the CI step is therefore conditional on an `NVD_API_KEY` repository secret being configured, skipping with a warning until a repo admin adds one. This gate does not actually activate until that one-time manual setup step is done.
- Jacoco coverage percentage gating is explicitly deferred to a future decision (see backlog `041-ci-cd-quality-gates.md` and `045-backend-unit-test-backfill.md`) — this feature only adds reporting.
