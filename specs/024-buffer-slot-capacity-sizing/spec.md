# Feature Specification: Buffer Slot Capacity Sizing

**Feature Branch**: `024-buffer-slot-capacity-sizing`

**Created**: 2026-09-03

**Status**: Draft

**Input**: User description: "022 — Buffer Slot Capacity Sizing: As the scheduling system, I want to size the number of buffer slots in each generated session based on a doctor's trailing no-show history, so that clinics can absorb walk-ins and late arrivals without over- or under-provisioning capacity. Trailing 90-day window, 5-sample floor (flat 1-slot default below it), risk score converted to up to 20% of a session's slots, absolute 3-slot cap, evenly distributed. Global constants, not clinic-configurable in v1."

## Clarifications

### Session 2026-09-03

- Q: How does a doctor's no-show rate convert into "up to 20% of a session's slots" — directly (capped at 20%), or scaled down by an additional 0.20 factor? → A: Direct cap — the no-show rate directly becomes the buffer percentage, capped at 20%. A scaled reading would make the risk-based calculation barely distinguishable from the flat default in practice, even for high-risk doctors.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A New/Low-History Doctor Gets the Flat Default Buffer (Priority: P1) 🎯 MVP

A doctor with fewer than 5 resolved bookings in the trailing 90 days (a brand-new doctor, or one with too little recent history) has a session generated for them, and gets a predictable, safe default buffer allocation rather than an unreliable computed value.

**Why this priority**: This is the floor every doctor starts at, and it's already almost entirely built (the existing cold-start calculator already returns this value) — verifying the upgraded system still produces it correctly under the new code path is the safest place to start and the one behavior this feature must never regress.

**Independent Test**: Generate a session for a doctor with 0–4 resolved bookings in the trailing 90 days and confirm exactly 1 buffer slot is allocated, evenly placed.

**Acceptance Scenarios**:

1. **Given** a doctor with fewer than 5 resolved bookings in the trailing 90 days, **When** a session is generated for them, **Then** exactly 1 buffer slot is allocated.
2. **Given** a doctor with zero booking history at all, **When** a session is generated for them, **Then** the same flat 1-slot default applies (zero is still "fewer than 5").

---

### User Story 2 - A Doctor With Sufficient History Gets a Risk-Based Buffer Count (Priority: P2)

A doctor with 5 or more resolved bookings in the trailing 90 days has a session generated for them, and the buffer count reflects their own recent no-show rate — more buffer for a doctor whose patients no-show more often, less (potentially zero) for one whose patients reliably show up — always within the 20%-of-session and 3-slot absolute caps.

**Why this priority**: This is the feature's actual value proposition, but it depends on User Story 1's infrastructure (the same calculation seam, the same even-distribution mechanism) already being correct.

**Independent Test**: Generate sessions for doctors with different constructed no-show rates (all with 5+ resolved bookings) and confirm the buffer count varies accordingly, never exceeding the 20%-of-session-slots or 3-slot caps.

**Acceptance Scenarios**:

1. **Given** a doctor with 5+ resolved bookings in the trailing 90 days and a nonzero no-show rate, **When** a session is generated, **Then** the buffer count is computed from that rate rather than the flat default.
2. **Given** a doctor's computed buffer count, from their no-show rate, would exceed 20% of the session's total slots, **When** it is applied, **Then** it is capped at 20% of the session's slots.
3. **Given** a doctor's computed buffer count would exceed 3 regardless of the 20% figure, **When** it is applied, **Then** it is capped at 3.
4. **Given** a doctor with 5+ resolved bookings and zero no-shows among them, **When** a session is generated, **Then** the computed buffer count may be 0 (no flat-default floor applies once the sample threshold is met).
5. **Given** N buffer slots are allocated (whether 1 from the flat default or a computed value), **When** they are placed among the session's slots, **Then** they are spread evenly across the timeline, never clustered at the end.

---

### Edge Cases

- What happens to a Queue/Token session? No buffer slots are computed for Queue/Token sessions — buffer slots are a Fixed-Time-only concept (`SlotGenerationService`, which this feature's calculator plugs into, is never invoked for Queue/Token sessions in the first place).
- What happens to bookings in the trailing window that are still in the future (not yet due, not yet resolved as no-show or otherwise)? They still count toward the 5-sample denominator (they are still "a resolved booking" in the sense of being a real scheduled appointment), but cannot themselves be a no-show yet since their grace period hasn't elapsed — this only affects the sample size, not the computed rate's correctness at the moment it's computed.
- What happens if a clinic or doctor is deleted or de-verified mid-window? Out of scope — this feature reads whatever `Slot`/`Booking` history already exists at generation time; it does not filter by a doctor's current verification/staffing status (that is 008/002/005's concern, not this one's).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: When a Fixed-Time session is generated for a doctor with fewer than 5 resolved bookings in the trailing 90 days, the system MUST allocate exactly 1 buffer slot.
- **FR-002**: When a Fixed-Time session is generated for a doctor with 5 or more resolved bookings in the trailing 90 days, the system MUST compute a buffer slot count from that doctor's no-show rate within the same window, rather than applying the flat default — the rate becomes the buffer percentage of the session's slots directly (Clarifications), before the 20% and 3-slot caps (FR-003/FR-004) are applied.
- **FR-003**: The computed buffer count MUST never exceed 20% of the session's total slot count.
- **FR-004**: The computed buffer count MUST never exceed 3, regardless of what the 20% calculation alone would produce.
- **FR-005**: The trailing window MUST be exactly 90 days ending at the session-generation run's own reference date.
- **FR-006**: Buffer slots MUST be distributed evenly across a session's slots, never clustered at the end — this requirement is already satisfied by existing, unchanged infrastructure this feature builds on.
- **FR-007**: None of the window length, sample floor, percentage ceiling, or absolute cap MUST be configurable per clinic in v1 — they are fixed, system-wide constants.
- **FR-008**: A no-show rate of zero with 5+ resolved bookings MUST be allowed to produce a computed buffer count of 0 — the flat 1-slot default applies only below the 5-sample threshold, never as a floor above it.

### Key Entities

- **Slot** *(existing, from 012/016/023)*: The unit this feature allocates as buffer capacity, and (via its `status = NO_SHOW`) the source of the no-show signal this feature's formula consumes.
- **Booking** *(existing, from 016)*: Identifies which Slots in the trailing window were actually resolved bookings (the sample-size denominator) versus never-booked Slots, and which doctor each belongs to (via its Slot's Session).
- **Session** *(existing, from 015)*: The unit a buffer count is computed for and applied to; only Fixed-Time sessions are in scope.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every Fixed-Time session generated for a doctor with fewer than 5 resolved trailing-90-day bookings receives exactly 1 buffer slot, with no exceptions.
- **SC-002**: Every Fixed-Time session generated for a doctor with 5+ resolved trailing-90-day bookings receives a buffer count that never exceeds the lesser of 20% of that session's slots and 3 slots.
- **SC-003**: A clinic administrator who looks for a way to change the lookback window, sample floor, percentage ceiling, or absolute cap finds no such setting anywhere in the product.
- **SC-004**: Buffer slot placement remains evenly distributed across every session's timeline regardless of which sizing path (flat default or computed) produced the count.

## Assumptions

- "5 data points" means 5 total resolved bookings for that doctor in the trailing window (the sample size underlying a meaningful rate) — not literally 5 no-show events, which would be an unusually high, rarely-crossed bar for most doctors.
- A "resolved booking" for sample-size purposes is any `Booking` whose Slot falls within the trailing 90-day window, regardless of whether that Slot's own status is currently `NO_SHOW` or still simply booked (a booking not yet due is still a real, countable data point about that doctor's practice, even though it cannot itself be a no-show yet).
- Reuses 023's `Slot.status = NO_SHOW` as the sole no-show signal, 016's `Booking` as the sole record of "this Slot was actually booked," and the already-existing buffer-count-consumption and even-distribution logic entirely as-is — this feature only supplies a new way to compute the *count*, not any of the surrounding mechanics.
- This feature ships no UI and no endpoint — it is an internal computation invoked automatically as part of the existing, already-automatic session-generation process, exactly as the doctor's/clinic's own use of buffer slots was never itself a manual action.
