# Implementation Plan: CI/CD Quality Gates

**Branch**: `044-ci-quality-gates` | **Date**: 2026-09-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/044-ci-quality-gates/spec.md`

## Summary

Add `.github/dependabot.yml` (gradle + npm ecosystems), an OWASP Dependency-Check Gradle plugin gate (fails on CVSS ≥ 7) to the backend CI job, an `npm audit --audit-level=high` step to the frontend CI job (reports but doesn't fail on the current moderate finding), and Jacoco coverage reporting (report-only, no gate) to the backend CI job. All four are additions to the existing `.github/workflows/ci.yml` and `backend/build.gradle` — no existing job step is removed or changed.

## Technical Context

**Language/Version**: Existing stack unchanged (Java 21 / Gradle backend, Node 20 / npm frontend); this feature only adds build-tool plugins and a CI/Dependabot config, no application language change.

**Primary Dependencies**: `org.owasp:dependency-check-gradle` (Gradle plugin, new), Jacoco (Gradle's own built-in plugin, new), npm's built-in `audit` command (already available, no new dependency), GitHub's built-in Dependabot (no dependency, a platform feature).

**Storage**: N/A.

**Testing**: No new test suite — this feature's own correctness is proven by the CI pipeline itself running successfully with the new steps included (a meta-verification, consistent with the nature of CI tooling).

**Target Platform**: GitHub Actions `ubuntu-latest` runners (existing CI environment, unchanged).

**Project Type**: Existing web application (backend + frontend); this feature only touches `backend/build.gradle`, `.github/workflows/ci.yml`, and adds `.github/dependabot.yml`.

**Performance Goals**: N/A — CI run-time increase should stay reasonable (OWASP Dependency-Check's NVD data sync is the slowest new step; plan should use its caching mechanism to avoid a multi-minute penalty on every run).

**Constraints**: MUST NOT change behavior of `gradlew spotlessCheck`/`gradlew test`/`npm run lint`/`npx tsc -b`/`npm run test` — only add new steps/plugins alongside them. MUST NOT fail CI on the frontend's known existing moderate finding.

**Scale/Scope**: One new file (`.github/dependabot.yml`), edits to `backend/build.gradle` (2 new plugins + their config) and `.github/workflows/ci.yml` (3 new steps: OWASP scan, npm audit, Jacoco report + artifact upload).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Test-First)**: Not triggered — no backend business logic or migration is added; this is build-tooling configuration. CI running green with the new steps included is this feature's own verification.
- **Principle II (Simplicity & YAGNI)**: PASS — Jacoco is report-only per FR-004/spec Assumptions (explicitly not gating, since most backend modules have no unit tests yet — gating now would be gating on a number no one can move); OWASP threshold set at the minimum severity that's actually actionable (CVSS ≥ 7) rather than failing on every low-severity noise finding.
- **Principle III (Modular Architecture)**: N/A.
- **Principle IV (Data Privacy & Integrity)**: N/A.

No violations. No Complexity Tracking entries needed.

## Project Structure

### Documentation (this feature)

```text
specs/044-ci-quality-gates/
├── plan.md
├── research.md
├── data-model.md    # N/A — no data model
├── quickstart.md
└── tasks.md
```

No `contracts/` — this feature has no API/CLI/UI surface; `.github/dependabot.yml` and CI workflow YAML are platform configuration, not a contract other application code depends on.

### Source Code (repository root)

```text
.github/
├── dependabot.yml          # NEW — gradle + npm ecosystem updates
└── workflows/
    └── ci.yml               # EDITED — 3 new steps (OWASP scan, npm audit, Jacoco)

backend/
└── build.gradle             # EDITED — add org.owasp.dependencycheck and jacoco plugins
```

No changes to any application source file, `application.yml`, Flyway migration, or frontend source.

**Structure Decision**: Existing backend+frontend web application structure unchanged; this feature only touches root-level CI/dependency-bot configuration and one build file.

## Complexity Tracking

No Constitution Check violations — this section is not applicable.
