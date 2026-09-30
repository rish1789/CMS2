# Implementation Plan: Queue Token Issuance Under Concurrent Requests

**Branch**: `claude/067-queue-token-issuance-race` | **Date**: 2026-09-30 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/067-queue-token-issuance-race/spec.md`

## Summary

Restore 019 FR-006/SC-002 and 022 SC-004: every simultaneous token request for an accepting session gets a distinct token, numbered 1..N with no gaps, on all three issuing paths (patient queue booking, staff queue booking, front-desk walk-in). Per the 2026-09-30 clarification, a queue booking and its token also become all-or-nothing, so a failed booking leaves no orphan token.

**Approach** (research.md):
- Take a row lock on the **Session** before reading `max(token) + 1`, and keep it until the booking or walk-in transaction commits. Collisions can no longer happen, so the ineffective retry loop is removed.
- Make both queue booking paths a single transaction, so a failed booking rolls its token back. Walk-in registration already is one.
- Bound the lock wait at 5 s. A timeout maps to the existing 503 `TOKEN_ISSUANCE_FAILED`.
- No schema change and no API change.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.3.5 (Spring Data JPA / Hibernate 6, Spring transactions)

**Storage**: PostgreSQL 16. The existing `slot`, `session` and `booking` tables are unchanged, and the unique index `uq_slot_session_token` stays as the safety net.

**Testing**: JUnit 5, Mockito unit tests, `@WebMvcTest` contract tests, Testcontainers integration tests (runnable locally with `api.version=1.44` and in CI)

**Target Platform**: Linux server (CI: GitHub Actions, ubuntu-latest)

**Project Type**: Web service (backend only for this feature)

**Performance Goals**: Uncontended booking unchanged (SC-004). A 20-request burst on one session completes with every request succeeding (SC-001). Requests serialize per session and each holds the lock for one booking's inserts.

**Constraints**:
- No second DB connection while holding the Session lock (research Decision 4, the lesson from #20).
- Lock order: patient account, then session.
- Bounded wait of 5 s (FR-007).

**Scale/Scope**: 3 service classes changed (`QueueSlotService`, `PatientQueueBookingService`, `StaffQueueBookingService`), 1 repository method added (`SessionRepository.findWithLockById`), `FrontDeskWalkInService` needs no logic change beyond the retry removal it inherits. Two contract documents get wording corrections.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design: still passes.*

| Principle | Assessment |
|---|---|
| **I. Test-First** | ✅ Research Decision 6 lists five new or tightened tests to be written and run **red** on current code before any fix (quickstart §1). The existing refusal tests must stay green. |
| **II. Simplicity & YAGNI** | ✅ The design *removes* code (the retry loop and `MAX_ATTEMPTS`) and adds one repository method, one `SET LOCAL` and one exception translation. The rejected alternatives (counter column, sequence, compensation) would each add more. No configurability: the 5 s bound is a constant. |
| **III. Modular architecture** | ✅ Token issuance stays in `scheduling` (`QueueSlotService`). The booking paths in `booking` keep calling it through its public API. The walk-in path stays in `booking`. No new cross-module reach-through. The existing booking → scheduling dependency direction is unchanged. |
| **IV. Data integrity** | ✅ The race is closed at the data layer: the Session row lock plus the unchanged unique index. No patient identity or clinical data is changed. 066's patient-linking lock is reused, and lock order is fixed. |
| Multi-tenancy | ✅ No new queries across tenants. The lock is taken on a session the callers have already loaded and tenant-checked (`filter(s -> s.getClinic().getId().equals(clinicId))` on the queue paths; walk-in checks the clinic). |
| Migrations | ✅ None. |
| Rationale note (scheduling/booking change) | ✅ It implements 019 FR-006/SC-002 and 022 SC-004, and supersedes 022 research's "two separate atomic units" decision. The reasons are recorded in research.md Decision 2: 064's `BOOKED` tokens made orphans harmful, and the retry closure no longer exists. |

**Explicit deviation to flag for review**: this plan overturns a documented earlier decision (022 research, "Slot-issuance and Booking-creation are two separate atomic units"). That is a deliberate, justified change, not silent drift. The 022 research file gets a dated pointer to 067 so the old decision isn't mistaken for current.

## Project Structure

### Documentation (this feature)

```text
specs/067-queue-token-issuance-race/
├── plan.md              # This file
├── research.md          # Phase 0 - current state, 6 decisions
├── data-model.md        # Phase 1 - no schema change; token lifecycle
├── quickstart.md        # Phase 1 - red/green commands, orphan check query
├── contracts/
│   └── token-issuance.md   # Behaviour changes on existing endpoints
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks - not created here)
```

### Source Code (repository root)

```text
backend/src/main/java/com/cms/
├── scheduling/
│   ├── repository/SessionRepository.java        # + findWithLockById (PESSIMISTIC_WRITE)
│   └── service/QueueSlotService.java            # lock session, SET LOCAL lock_timeout, no retry loop
└── booking/service/
    ├── PatientQueueBookingService.java          # patient + token + booking in one TransactionTemplate
    ├── StaffQueueBookingService.java            # same
    └── FrontDeskWalkInService.java              # no logic change (already one transaction)

backend/src/test/java/com/cms/
├── scheduling/integration/
│   ├── QueueSlotIssuanceConcurrencyTest.java    # tightened: 10 repeats x 20
│   └── TokenIssuanceLockTimeoutTest.java        # new
└── booking/integration/
    ├── QueueTokenConcurrentBookingTest.java     # new: patient + staff mixed
    ├── WalkInConcurrentRegistrationTest.java    # new: queue + fixed-time walk-in line
    └── QueueBookingFailureLeavesNoTokenTest.java # new

specs/022-queue-token-booking/research.md        # dated pointer: superseded by 067
specs/022-queue-token-booking/contracts/queue-booking.md  # TOKEN_ISSUANCE_FAILED meaning
specs/063-front-desk-walk-in/contracts/front-desk-walk-in.md  # 409 -> 503 doc fix
docs/product-audit/07-BUG-AND-DEFECT-REGISTER.md  # PB-003 resolved; orphan-token note
```

**Structure Decision**: This is the existing web-application layout, and only the backend changes. New tests follow the existing split: concurrency and integration tests in `integration/`, with unit tests updated where they stub the removed retry loop.

## Risks

- **Longer lock hold on the walk-in path**: the Session lock is held for the rest of the walk-in transaction, which includes the patient insert and booking insert. That's milliseconds, but it serializes walk-ins and bookings per session. Accepted: that serialization is the fix.
- **Session cancellation is not serialized with issuance, and this is unchanged and out of scope** (analysis F1, owner decision 2026-09-30: option a). Whole-session and partial cancellation (`SessionCancellationService`, `SessionPartialCancellationService`) record themselves in the separate `session_cancellation` table and never write the Session row, so they do not wait on the new lock. The availability check still runs before the lock, as today. A token issued at the same moment as a cancellation can therefore still land in a session that is cancelled a moment later. This is existing behaviour, and 067 does not make it worse. Closing it would mean the cancellation services taking the same lock. That interacts with their per-booking `REQUIRES_NEW` cancellations (research Decision 4), so it is left for a separate feature. The existing cancellation tests must stay green (T020, T026).
- **Behaviour visible to users**: a failed queue booking no longer consumes a token number. This was always the promised behaviour (019 SC-002), but anyone who noticed numbers being skipped will see them stop being skipped.
- **Pre-existing orphans** in real databases are not cleaned up. There is a read-only check query in the quickstart. Cleaning them up is a separate decision.

## Complexity Tracking

No constitution violations. The one deliberate reversal of a prior decision (022) is justified in research.md Decision 2 and flagged above.
