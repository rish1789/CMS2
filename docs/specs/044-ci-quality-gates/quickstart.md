# Quickstart: CI/CD Quality Gates

Most of this feature can only be fully verified once merged and running on GitHub Actions (Dependabot's own PR-opening behavior, in particular) — this sandbox has no live GitHub Actions runner or Dependabot integration. What follows is what can be verified locally plus what to check once live.

## Local verification

1. `cd backend && NVD_API_KEY=<your-free-key> ./gradlew dependencyCheckAnalyze` — requires a free NVD API key (https://nvd.nist.gov/developers/request-an-api-key); without one the scan fails outright with `Invalid API Key, length of 0` (confirmed 2026-09-15), it does not just run slowly. With a key, confirm it completes and produces a report under `backend/build/reports/dependency-check-report.html` with no High/Critical findings against the current dependency set (US2, SC-001's negative case). In CI, this step is skipped with a warning until an `NVD_API_KEY` repository secret is configured.
2. `cd backend && ./gradlew test jacocoTestReport` — confirm both succeed and a report appears under `backend/build/reports/jacoco/test/html/index.html` (US4, SC-004).
3. `cd frontend && npm audit --audit-level=high` — confirm exit code 0 today (the two known moderate findings do not trigger `--audit-level=high`) (US3, SC-003's non-blocking case).
4. `cd frontend && npm audit` (no level flag) — confirm the two known moderate `react-router` findings are visible in the output, proving they're not silently hidden, just not gating (US3, SC-003's visibility half).

## Verify once merged and live on GitHub

5. Check the repository's Dependabot tab/Insights → Dependency graph → Dependabot for scheduled scans on both `gradle` and `npm` ecosystems (US1, SC-002).
6. Open a test PR bumping a backend dependency to a version with a known CVSS ≥ 7 CVE (e.g. a deliberately old, known-vulnerable test dependency in a throwaway branch, never merged) and confirm the backend CI job fails with a clear report (US2, SC-001's positive case) — then delete the branch.
