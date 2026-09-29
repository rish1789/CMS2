# Implementation Plan: Backend Unit-Test Backfill

**Branch**: `048-backend-unit-tests` | **Date**: 2026-09-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/048-backend-unit-tests/spec.md`

## Summary

Add 4 pure-Mockito unit test classes (`booking.unit.FeeResolutionServiceTest`, `booking.unit.BookingCancellationServiceTest`, `scheduling.unit.ScheduleServiceTest`, `scheduling.unit.NoShowDetectionServiceTest`), mirroring `identity`/`patient`'s existing unit-test shape — no Spring context, no Docker, mocked repositories/collaborators. All 4 target classes already read in full during specify; no research unknowns remain about their logic.

## Technical Context

**Language/Version**: Java 21, JUnit 5, Mockito (all already on the classpath via `spring-boot-starter-test`).

**Primary Dependencies**: None new.

**Storage**: N/A — mocked repositories, no real database.

**Testing**: This feature IS test code — its own "testing" is that the new tests compile, run, and pass in this sandbox.

**Target Platform**: Backend, unchanged.

**Project Type**: Existing web application; adds 4 test files under `backend/src/test/java/com/cms/{booking,scheduling}/unit/`.

**Performance Goals**: N/A.

**Constraints**: Zero change to any class under test's actual behavior (test-only addition); each test must run without Docker.

**Scale/Scope**: 4 new test classes, 0 production files touched (unless a genuine bug is found, per FR-006).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Test-First)**: This feature directly serves Principle I's own stated rationale (fast, dependency-free feedback for precise, easy-to-regress business rules) — it is itself a test-infrastructure investment, not a case where Principle I applies to itself recursively.
- **Principle II (Simplicity & YAGNI)**: PASS — 4 classes chosen for genuine complexity, not padding; no test-only abstraction/helper framework introduced beyond what `identity`/`patient`'s existing unit tests already establish as this project's convention.
- **Principle III (Modular Architecture)**: PASS — tests live inside their own module's test tree (`booking.unit`, `scheduling.unit`), no cross-module test coupling.
- **Principle IV (Data Privacy & Integrity)**: N/A — no real data, mocked collaborators only.

No violations. No Complexity Tracking entries needed.

## Project Structure

### Documentation (this feature)

```text
specs/048-backend-unit-tests/
├── plan.md
├── research.md
├── data-model.md    # N/A — no data model
├── quickstart.md
└── tasks.md
```

No `contracts/` — this feature adds no API/CLI surface.

### Source Code (repository root)

```text
backend/src/test/java/com/cms/booking/unit/
├── FeeResolutionServiceTest.java        # NEW
└── BookingCancellationServiceTest.java  # NEW

backend/src/test/java/com/cms/scheduling/unit/
├── ScheduleServiceTest.java             # NEW
└── NoShowDetectionServiceTest.java      # NEW
```

**Structure Decision**: Matches `identity`/`patient`'s existing `unit/` subfolder convention exactly — the first `unit/` folders in `booking`/`scheduling`, establishing the pattern 045's remaining follow-up work will continue.

## Complexity Tracking

No Constitution Check violations — this section is not applicable.
