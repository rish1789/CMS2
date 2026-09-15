# Feature Specification: Queue Position Tracking (Queue-Mode Only)

**Feature Branch**: `027-queue-position-tracking`

**Created**: 2026-09-04

**Status**: Draft

**Input**: User description: "024 — Queue Position Tracking (Queue-Mode Only): As a patient or staff member in a queue/token-mode session, I want to know a booking's current position in the queue. Queue position = count of active bookings ahead of this booking in token order, + 1. 'Active' means bookings ahead that are not yet completed, cancelled, or no-show. No delay-in-minutes figure exists for queue mode — position (a count, not a time estimate) is the equivalent signal. Applies only to Queue/Token-mode sessions; Fixed-Time sessions use delay tracking (023) instead."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Staff Views a Booking's Queue Position (Priority: P1) 🎯 MVP

Front-desk staff or the doctor check how many active bookings remain ahead of a given queue-mode booking, so they can answer a waiting patient's "how much longer?" question.

**Why this priority**: This is the feature's core value and the first, most general way the position figure gets used — without it, nothing else in this feature has anywhere to be observed.

**Independent Test**: With a Queue-mode Session that has 5 tokens booked, tokens 1-2 completed and token 3 still active, query token 5's Booking's position and confirm it equals 3 (tokens 3 and 4 active ahead, +1).

**Acceptance Scenarios**:

1. **Given** a Queue-mode Session with tokens 1-5 booked, tokens 1-2 marked completed and token 3 still active (BOOKED), **When** staff query token 5's queue position, **Then** the system reports position 3 (active bookings ahead: tokens 3 and 4, +1).
2. **Given** an active booking ahead in the queue is later marked no-show, **When** queue position is next queried for a later token, **Then** the reported position is one lower than before, reflecting one fewer active booking ahead.
3. **Given** a Fixed-Time Session's Booking, **When** its queue position is requested, **Then** the response indicates queue position does not apply — Fixed-Time Sessions use delay tracking (023) instead.
4. **Given** a queue-mode Booking whose own Slot is already completed or no-show, **When** its queue position is queried, **Then** the response indicates the queue position is no longer applicable to it (already resolved) rather than a numeric count.

---

### User Story 2 - Patient Views Their Own Booking's Queue Position (Priority: P2)

A patient checks their own queue-mode booking's current position directly, without needing to ask staff.

**Why this priority**: Depends on User Story 1's computation already existing; this only adds a second, self-service access path to the same figure, scoped to the patient's own booking.

**Independent Test**: As an authenticated patient with a queue-mode booking, query its position and confirm it matches what staff would see for the same booking; attempt to query a different patient's booking and confirm it's rejected.

**Acceptance Scenarios**:

1. **Given** an authenticated patient with a queue-mode Booking, **When** they query its queue position, **Then** they see the identical figure staff would see for that same Booking.
2. **Given** an authenticated patient, **When** they attempt to query a queue position for a Booking that isn't theirs, **Then** the request is rejected.

---

### Edge Cases

- What happens when a queue-mode Booking has no other tokens booked ahead of it at all? Position is 1 (zero active bookings ahead, +1).
- What happens when every token ahead has already been completed or marked no-show? Position is 1, the same as having no tokens ahead at all.
- What happens to a "cancelled" booking ahead in the queue? No booking-cancellation capability exists anywhere in this codebase yet (scheduled later in the build order) — for now, only completed and no-show states are excluded from "active." A future cancellation feature will need to extend this exclusion set when it introduces a cancelled state.
- What happens when the position is queried while a trigger (a token ahead being completed) happens concurrently? The next query simply reflects whatever is currently stored — this figure is computed fresh on every query, unlike 023's stored-and-triggered delay figure, so there's no staleness window to reason about.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: For a queue-mode Booking, the system MUST compute its queue position as the count of active bookings whose token number is lower (ahead) in the same Session, plus one.
- **FR-002**: "Active" MUST mean the ahead booking's Slot status is neither completed nor no-show (Edge Cases — no cancelled state exists yet to also exclude).
- **FR-003**: The system MUST compute queue position freshly on every query — never from a stored or cached prior value — so a query always reflects the current state of all tokens ahead.
- **FR-004**: The system MUST allow staff at the Booking's clinic to query any queue-mode Booking's position.
- **FR-005**: The system MUST allow an authenticated patient to query the queue position of a Booking that is their own, and MUST reject a query for a Booking that is not theirs.
- **FR-006**: A queue position query for a Fixed-Time Session's Booking MUST indicate the concept does not apply, not a numeric value — Fixed-Time Sessions use delay tracking (023) instead.
- **FR-007**: A queue position query for a Booking whose own Slot is already completed or no-show MUST indicate the position is no longer applicable, not a numeric value.

### Key Entities

- **Slot** *(existing, from 013/019/021)*: The unit whose `tokenNumber` and `status` (`OPEN`/`BOOKED`/`NO_SHOW`/`COMPLETED`) this feature reads to compute position — read-only for this feature, no new fields.
- **Booking** *(existing, from 016/018)*: The record this feature reports a position for, via its associated Slot.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A queried queue position always equals the exact count of currently-active bookings with a lower token number in the same Session, plus one.
- **SC-002**: Two queries for the same Booking separated by a change in an ahead booking's active/resolved state always reflect that change on the very next query — no staleness.
- **SC-003**: 100% of queue position queries for a Fixed-Time Session's Booking indicate "not applicable," never a numeric value.
- **SC-004**: 100% of a patient's attempts to query a queue position for a Booking that isn't theirs are rejected.
- **SC-005**: A staff-observed position and a patient-observed position for the identical Booking, queried at the same moment, are always identical.

## Assumptions

- Position is computed live at query time, never stored/cached — a direct reading of the business rule's own "recalculates" wording, and a deliberate contrast with 023's stored-and-triggered delay figure (023 needs staleness-until-trigger; this feature has no such requirement and computing live is simpler — Constitution II).
- "Active" currently excludes only completed and no-show Slot states (Assumptions/Edge Cases) — no cancelled state exists anywhere in this codebase yet; cancellation features later in build-order.md will need to extend this exclusion set when they introduce one.
- Staff viewing is unrestricted by role beyond authentication (any active staff member at the clinic, doctor included) — mirrors 026's own precedent that viewing (as opposed to a state-changing action) doesn't need the narrower Operations/ClinicAdmin-only gate.
- A patient may only query their own Booking's position — reusing the existing patient-authenticated access pattern already established by 017/018's patient-facing booking endpoints.
- This feature ships a minimal frontend display (a position indicator) on both the staff and patient sides, consistent with prior features' precedent of shipping a view alongside the endpoint that makes it reachable.
