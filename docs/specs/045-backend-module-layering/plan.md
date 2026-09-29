# Implementation Plan: Backend Module Layering & Security Posture Documentation

**Branch**: `045-backend-module-layering` | **Date**: 2026-09-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/045-backend-module-layering/spec.md`

## Summary

Reorganize 11 backend modules whose package root exceeds ~10 files into `api/`/`service/`/`repository/`/`domain/`/`exception/`/`config/` subpackages (existing `dto/` subpackages untouched), smallest module first per the architect review's own guidance, verifying compilation (and the Docker-independent test subset) after each move. Add `spring-boot-starter-actuator` exposing only `/actuator/health`. Write one `SECURITY.md` indexing the 5 existing `SecurityConfig` classes. Zero behavior change — package/file moves and import fixes only.

## Technical Context

**Language/Version**: Java 21 / Spring Boot 3.3.5 / Gradle (unchanged).

**Primary Dependencies**: `spring-boot-starter-actuator` (new); no other new dependency.

**Storage**: N/A — no schema/migration change.

**Testing**: Existing test suite is the correctness proof at each step (per constitution — a passing suite after each mechanical move is what verifies "no behavior change"). This sandbox's confirmed Docker/Testcontainers limitation means only the Docker-independent unit/contract subset can be run directly here; compilation success plus that subset's pass is this session's own verification bar, full-suite confirmation deferred to real CI.

**Target Platform**: Unchanged (existing backend).

**Project Type**: Existing web application; this feature touches only `backend/src/main/java/com/cms/**` package structure (and its mirror in `backend/src/test/java/com/cms/**` for import fixes only, test file *contents* otherwise unchanged), `backend/build.gradle` (actuator dependency), `application.yml`/relevant `SecurityConfig` (actuator exposure), and a new root-level `SECURITY.md`.

**Performance Goals**: N/A.

**Constraints**: Zero behavior change; every moved file's package declaration and every referencing import (main and test source) must be updated consistently; the full existing test suite (where runnable) must pass after each module's move.

**Scale/Scope**: 11 modules reorganized (~330 files moved: booking 60+24dto, scheduling 41+6dto, identity/admin 26, clinical 21+8dto, waitlist 18+5dto, patient/record 15, identity/staff 15, identity/account 15, patient/account 14, inbox 12+1dto, notification 11 — dto subpackages themselves untouched, counted only for context). 2 smaller modules (identity/clinic 8, common 8) and 4 tiny ones (identity/doctor 5, discovery 5, identity/api 2, patient/api 2) stay flat per FR-002.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Test-First)**: Applied in its "prove no regression" sense rather than literally — no new business behavior is added, so there is no new test to write first; instead the existing test suite for each module is the pre-existing red/green baseline this refactor must not break, checked after every module's move (not batched at the end).
- **Principle II (Simplicity & YAGNI)**: PASS — the ~10-file threshold (FR-002) deliberately avoids fragmenting small modules into six near-empty folders; only modules with a real "which file has X" problem get the treatment.
- **Principle III (Modular, Library-First Architecture)**: This feature is a direct, explicit application of this principle — fixing inconsistent *internal* layering while explicitly preserving the existing top-level module boundaries (no flattening into a horizontal controllers/services/repositories split, which the roadmap explicitly warned against as fighting the constitution).
- **Principle IV (Data Privacy & Integrity)**: N/A — no schema, migration, or clinical-data-handling logic changes; a pure structural move.

No violations. No Complexity Tracking entries needed.

## Project Structure

### Documentation (this feature)

```text
specs/045-backend-module-layering/
├── plan.md
├── research.md
├── data-model.md    # N/A — no data model
├── quickstart.md
└── tasks.md
```

No `contracts/` — no new API/CLI surface (the actuator health endpoint is covered by an acceptance scenario, not a full contract doc, given its trivial shape).

### Source Code (repository root)

```text
backend/src/main/java/com/cms/<module>/
├── api/            # @RestController classes (was flat)
├── dto/            # UNCHANGED — already a subpackage everywhere it exists
├── service/        # @Service, event listeners, @Scheduled triggers, validators/generators,
│                   # calculator/sender interfaces + impls (was flat)
├── repository/     # Spring Data interfaces (was flat)
├── domain/         # @Entity, enums, domain event POJOs (was flat)
├── exception/      # exceptions + @RestControllerAdvice (was flat)
└── config/         # SecurityConfig + its JwtService/AuthenticationFilter/EntryPoint (was flat)

backend/build.gradle              # EDITED — add spring-boot-starter-actuator
backend/src/main/resources/application.yml   # EDITED — actuator endpoint exposure
SECURITY.md                        # NEW — repo root, indexes all 5 SecurityConfig classes
```

Applied to: `booking`, `scheduling`, `identity/admin`, `clinical`, `waitlist`, `patient/record`, `identity/staff`, `identity/account`, `patient/account`, `inbox`, `notification` (11 modules, smallest-file-count first per research.md). Left flat: `identity/clinic`, `common`, `identity/doctor`, `discovery`, `identity/api`, `patient/api` (≤10 files each).

**Structure Decision**: Existing package-per-feature top-level structure (Constitution Principle III) is unchanged — this feature only fixes *internal* layering within modules that have outgrown a flat package, per the architect review's own explicit recommendation against flattening into a horizontal split.

## Complexity Tracking

No Constitution Check violations — this section is not applicable.
