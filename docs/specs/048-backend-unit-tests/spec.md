# Feature Specification: Backend Unit-Test Backfill

**Feature Branch**: `048-backend-unit-tests`

**Created**: 2026-09-15

**Status**: Draft

**Input**: User description: "backlog/045-backend-unit-test-backfill.md" — add dependency-free (no Docker/Testcontainers) unit tests for `booking` and `scheduling`, the two "business-critical" modules named by the backlog's own priority, currently 100% integration-test-only. Sixth and final feature of the 040-045 production-hardening sub-wave.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Fee resolution's override/default/block rule is regression-tested without Docker (Priority: P1)

A developer changing anything near booking's fee logic wants a fast, dependency-free test proving the override→default→hard-block precedence still holds, without needing a running Postgres instance.

**Why this priority**: `FeeResolutionService.resolve` is a small, pure business-rule method (no HTTP, minimal collaborators) — the cheapest, highest-value starting point, and the exact rule (017 FR-001..FR-004) this project has repeatedly flagged as easy to silently regress.

**Independent Test**: Run the new test class directly (`gradle test --tests`) with no Docker running and confirm it passes.

**Acceptance Scenarios**:

1. **Given** an appointment type with a fee override set, **When** `resolve` is called, **Then** the override amount is returned, regardless of any doctor default fee.
2. **Given** an appointment type with no override but the doctor has a default fee configured, **When** `resolve` is called, **Then** the default fee is returned.
3. **Given** neither an override nor a doctor default fee exists, **When** `resolve` is called, **Then** a hard-block exception is thrown — never a zero or fabricated amount.
4. **Given** an appointment type that belongs to a different doctor than requested, **When** `resolve` is called, **Then** it is treated as not found.

---

### User Story 2 - Booking cancellation's gates and race-closure are regression-tested without Docker (Priority: P1)

A developer changing anything near cancellation logic wants dependency-free tests proving the Fixed-Time-only gate, the already-cancelled/race-lost rejection, and the successful slot-reopening + event-publishing path all still hold.

**Why this priority**: `BookingCancellationService` carries this project's own documented history of subtle, previously-shipped bugs in this exact class (a detached-entity lost-update bug, a rollback-poisoning bug) — the highest-value class in `booking` to protect with a fast test loop.

**Independent Test**: Run the new test class directly with no Docker running and confirm it passes.

**Acceptance Scenarios**:

1. **Given** a booking on a Queue-mode session, **When** cancellation is attempted, **Then** it is rejected (Fixed-Time-only).
2. **Given** a booking whose slot is not currently `BOOKED`, **When** cancellation is attempted, **Then** it is rejected.
3. **Given** a booking that loses the data-layer race (the guarded update affects zero rows), **When** cancellation is attempted, **Then** it is rejected, not silently treated as success.
4. **Given** a valid, currently-booked Fixed-Time booking, **When** cancellation succeeds, **Then** the slot is reopened, the booking's in-memory status reflects the cancellation, and a cancellation event is published.

---

### User Story 3 - Schedule validation and overlap detection are regression-tested without Docker (Priority: P2)

A developer changing schedule validation or the overlap-detection rule wants dependency-free tests proving both still hold, without a real Postgres instance to exercise the advisory-lock path (which stays integration-only, since it's inherently a real-database concern).

**Why this priority**: `ScheduleService` is `scheduling`'s largest, most complex class, with a documented history of a real security-gap bug found during this project's own implementation; second priority behind `booking` per the backlog's own stated order.

**Independent Test**: Run the new test class directly with no Docker running and confirm it passes.

**Acceptance Scenarios**:

1. **Given** a schedule request with no days of week, a non-strictly-increasing time range, a missing slot interval in Fixed-Time mode, or a slot interval set in Queue mode, **When** validated, **Then** each is rejected with a specific reason.
2. **Given** an existing schedule sharing a day of week with an overlapping time range for the same doctor (across any clinic), **When** a new/edited schedule is checked, **Then** it is rejected.
3. **Given** an existing schedule whose time range only touches (never overlaps) the new one, **When** checked, **Then** it is accepted (half-open interval semantics).
4. **Given** an edit excluding the schedule's own prior state from the overlap comparison, **When** checked against only itself, **Then** it is accepted.

---

### User Story 4 - No-show detection's grace-period boundary is regression-tested without Docker (Priority: P2)

A developer changing the no-show grace period wants a dependency-free test proving the exact boundary condition (still within grace vs. past grace) still holds.

**Why this priority**: Small, self-contained, and the exact kind of off-by-one/boundary logic most prone to silent regression.

**Independent Test**: Run the new test class directly with no Docker running and confirm it passes.

**Acceptance Scenarios**:

1. **Given** a booked Fixed-Time slot whose scheduled time plus the grace period has not yet passed, **When** the sweep runs, **Then** it is not marked no-show.
2. **Given** a booked Fixed-Time slot whose scheduled time plus the grace period has passed, **When** the sweep runs, **Then** it is marked no-show and the returned count reflects it.

---

### Edge Cases

- What happens to classes this pass doesn't cover (the other ~55 booking files and ~38 scheduling files, plus the 5 other integration-only modules)? Explicitly out of scope for this pass, stated here and in `backlog/progress.md` as a real, intentional follow-up — not silently dropped.
- What happens when a targeted method's dependencies include a native-query call (`ScheduleService.lockDoctorForOverlapCheck`, using `EntityManager`)? That specific method is inherently a real-database concern (a Postgres advisory lock) and stays integration-test-only; the *other* logic in the same class (validation, overlap detection) is still unit-testable by mocking `EntityManager` just enough to let `create`/`edit` proceed past that call.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `FeeResolutionService.resolve` MUST have unit tests (pure Mockito, no Spring context) covering the override, default, and hard-block cases, plus the wrong-doctor case.
- **FR-002**: `BookingCancellationService` MUST have unit tests covering the mode gate, status gate, race-loss rejection, and the successful path's slot-reopen + event-publish side effects.
- **FR-003**: `ScheduleService`'s validation and overlap-detection logic MUST have unit tests covering every validation rule and the shared-day/time-overlap/touching-boundary/self-exclusion cases.
- **FR-004**: `NoShowDetectionService.detectAndMarkNoShows` MUST have a unit test covering the grace-period boundary in both directions.
- **FR-005**: All new tests MUST run and pass in an environment with no Docker available (verified directly in this sandbox).
- **FR-006**: None of these tests may modify the classes under test's actual behavior — this is a test-only addition, not a refactor (unless a genuine bug is found while writing a test, in which case it's reported and fixed with the user's awareness, consistent with this project's established convergence-pass precedent).

### Key Entities

N/A — no data model changes; this feature adds test code only.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 4 new unit test classes (one per targeted service) exist and pass in this sandbox with zero Docker dependency.
- **SC-002**: Every acceptance scenario listed above has a corresponding passing test.
- **SC-003**: The full existing test suite (Docker-independent subset, verified in this sandbox; full suite compiling cleanly) shows zero regressions.
- **SC-004**: Any genuine bug found while writing these tests is reported explicitly, not silently fixed-and-hidden.

## Assumptions

- Scope is deliberately 4 classes (2 in `booking`, 2 in `scheduling`), not the full 7-module, all-classes backfill the original backlog entry described — per that entry's own anticipation ("this feature will likely need to be split into multiple spec-kit passes... flag this explicitly during planning rather than attempting a single unmanageable task list"). The remaining classes and the other 5 modules (`waitlist`, `clinical`, `discovery`, `notification`, `inbox`) are a real, explicit follow-up, tracked in `backlog/progress.md`, not claimed complete here.
- Classes chosen are the ones with the most genuine conditional/business-rule complexity in the two priority modules (verified by direct reading, not guessed), per the backlog's own "prioritize services with real conditional logic... over pure data-holder classes or trivial pass-through repositories" guidance.
- `ScheduleService.lockDoctorForOverlapCheck`'s Postgres advisory-lock call stays untested at the unit level (inherently a real-database concern) — its surrounding validation/overlap logic is still covered by mocking `EntityManager` just enough to let execution proceed past it.
