# Research: CI/CD Quality Gates

## Decision 1: OWASP Dependency-Check severity threshold

**Decision**: `failBuildOnCVSS = 7` (fails only on High/Critical, CVSS ≥ 7.0), using the `org.owasp.dependencycheck` Gradle plugin with its NVD data cache enabled to avoid re-downloading the vulnerability database on every run.

**Rationale**: CVSS 7 is the conventional "High" boundary and the threshold the backlog itself suggested as a default. No current backend dependency is known to trigger it (unverifiable with a live NVD sync in this network-restricted sandbox — final confirmation happens on the first real CI run, per this project's established pattern for anything needing external network/service access this sandbox can't fully exercise).

**Alternatives considered**: Failing on any finding (Medium+) — rejected as too noisy for a first rollout; Snyk or GitHub's own Dependency Review Action instead of OWASP Dependency-Check — rejected, OWASP's plugin is free/OSS with no external account needed, matching the backlog's "zero-budget tooling" framing exactly.

**Confirmed during implementation (2026-09-15)**: the NVD has required an API key for reliable data access since February 2023 — without one, `dependencyCheckAnalyze` doesn't run slowly, it **fails outright** (`NvdApiException: Invalid API Key, length of 0 too short`), confirmed via a live local run in this sandbox. Since no `NVD_API_KEY` secret exists yet on the live GitHub repo, shipping the CI step unconditionally would have permanently broken every future push/PR with no way to merge — a materially worse outcome than not having the gate at all. Resolved by making the CI step conditional on `secrets.NVD_API_KEY` being set: skip with a clear `::warning::` log line (pointing to the free key-request URL) when absent, run for real when present. "Zero-budget" still holds — the key itself is free — but it requires one manual one-time setup step from a repo admin that this feature cannot perform on its own (no credential-entry action is available to an agent).

## Decision 2: npm audit threshold and failure mode

**Decision**: `npm audit --audit-level=high` as the CI step — npm's own built-in severity filter, which exits non-zero only when a finding at or above "high" exists.

**Rationale**: A live `npm audit` run (2026-09-15) confirmed exactly 2 moderate-severity findings today (`react-router`/`react-router-dom`, GHSA-wrjc-x8rr-h8h6 and GHSA-337j-9hxr-rhxg), whose only fix is a breaking major-version bump — out of scope for a CI-tooling feature. `--audit-level=high` is npm's native mechanism for exactly this need (report everything, fail only above a threshold) — no custom scripting required.

**Alternatives considered**: `npm audit` with no level flag (fails on any finding, including the existing moderate ones) — rejected, would break CI immediately on merge for a pre-existing, out-of-scope issue; suppressing the finding via an audit-exclusion file — rejected as unnecessary complexity when the built-in `--audit-level` flag already does the right thing with zero extra config.

## Decision 3: Jacoco reporting scope

**Decision**: Apply the Jacoco plugin to the root `backend/build.gradle`, generate an XML+HTML report via `jacocoTestReport` (wired to run after `test`), and upload the HTML report as a CI build artifact. No `jacocoTestCoverageVerification` rule is configured (that's the gating mechanism, explicitly deferred).

**Rationale**: Matches FR-004 exactly — visibility without a gate. Uses Jacoco's default XML+HTML output (no external service like Codecov/Coveralls needed, keeping this zero-budget per the roadmap's own framing).

**Alternatives considered**: Wiring a coverage-percentage gate now with a very low threshold (e.g. 5%) — rejected; even a token gate is scope creep against FR-004's explicit "does not affect build pass/fail status," and would need to be revisited the moment 045 changes what's measurable anyway.

## Decision 4: Dependabot schedule

**Decision**: Weekly schedule for both `gradle` and `npm` ecosystem entries in `.github/dependabot.yml`.

**Rationale**: Standard, low-noise default for a project at this scale — daily would generate excessive PR volume for a small maintainer team; weekly is GitHub's own commonly-recommended default and needs no further justification given no specific cadence was requested.

**Alternatives considered**: Daily — rejected as unnecessarily noisy; monthly — rejected as too slow to catch a fast-moving high-severity CVE in a timely PR.
