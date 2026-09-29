# Feature Specification: Remove Reserved-Capacity Walk-In Slots

**Feature Branch**: `058-remove-reserved-capacity`

**Created**: 2026-09-22

**Status**: Draft

**Input**: User description: "Remove the buffer-slot / reserved-capacity mechanism entirely (introduced by feature 018, sized by feature 024's risk-based calculator, consumed by feature 025's walk-in insertion priority search). No slot should ever be generated as non-directly-bookable 'reserved capacity' again — every Fixed-Time slot is equally bookable by patients and staff from the moment it's generated. Walk-in insertion (025) no longer gets a first-priority tier of buffer slots to claim; it falls back to its remaining priority order (a no-show-freed slot, then any other open slot with an override reason)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Every generated slot is directly bookable (Priority: P1)

As a patient or staff member booking a Fixed-Time appointment, I want every slot shown to me to be bookable, so that I never hit a slot that looks open but silently refuses my booking with no clear reason.

**Why this priority**: This is the core confusion this change removes — a slot that displays as part of the day's schedule but can't be booked directly is the exact behavior being eliminated.

**Independent Test**: Generate a new day's Fixed-Time sessions and confirm every resulting slot can be booked directly by a patient or by staff, with none rejected for being reserved capacity.

**Acceptance Scenarios**:

1. **Given** a newly generated Fixed-Time session, **When** a patient views its slots, **Then** every slot is offered as directly bookable — none are marked as reserved or unbookable.
2. **Given** a newly generated Fixed-Time session, **When** staff attempt to book any slot directly, **Then** the booking succeeds (subject to the slot's normal status/eligibility rules, unrelated to this change).
3. **Given** the Day Sheet view, **When** staff look at any slot row, **Then** no "reserved capacity" or equivalent label appears anywhere.

---

### User Story 2 - Walk-ins still get placed sensibly, without a reserved tier (Priority: P2)

As front-desk staff inserting a walk-in patient, I want the system to still find a sensible slot for them (preferring a slot freed by a no-show over displacing a different patient's booking), so that walk-in placement stays orderly even though there's no longer a pool of slots held back specifically for this purpose.

**Why this priority**: Walk-in insertion is the one existing workflow that depended on reserved capacity; it must keep working without regressing into overwriting other patients' visits by default.

**Independent Test**: With no reserved slots available (since none exist anymore), insert a walk-in and confirm it's offered a no-show-freed slot when one exists, and otherwise a regular open slot with an explicit override reason — never a silent double-booking.

**Acceptance Scenarios**:

1. **Given** a session with a slot freed by an earlier no-show, **When** staff insert a walk-in, **Then** the walk-in is placed into that freed slot first.
2. **Given** a session with no no-show-freed slot but at least one other open slot, **When** staff insert a walk-in, **Then** the walk-in can be placed into that open slot, with the same override-reason requirement this already carries today.
3. **Given** a session with no open slot of any kind, **When** staff attempt to insert a walk-in, **Then** the system behaves exactly as it already does today for that case (unrelated to this change).

---

### User Story 3 - No-show handling no longer factors into slot generation sizing (Priority: P3)

As a clinic operator, I don't want a doctor's historical no-show rate to influence how many slots get held back from booking, since that mechanism is being removed along with reserved capacity.

**Why this priority**: This is a natural consequence of removing the mechanism rather than a separately valuable outcome, so it's lowest priority — but it's worth confirming explicitly since it's the one place no-show history fed into scheduling generation itself (as opposed to same-day slot-status detection, which is untouched).

**Independent Test**: Generate sessions for a doctor with a high recent no-show rate and confirm slot generation produces the same count and spacing of slots as for any other doctor — no slots are held back based on that history.

**Acceptance Scenarios**:

1. **Given** a doctor with a high recent no-show rate, **When** their next sessions are generated, **Then** the resulting slots are identical in count and timing to what a doctor with no no-show history would get for the same schedule.

---

### Edge Cases

- What happens to slots that were already generated as reserved capacity before this change ships, for sessions on today or a future date? They become ordinary, directly bookable slots — no distinct handling, no backfill action required, and no visible difference from any other slot.
- What happens to a booking or walk-in insertion already in progress against a reserved-capacity slot at the moment this change ships? Out of scope — this is a same-day operational edge case with negligible likelihood, not a data-integrity concern (once the change ships, that slot is simply an ordinary slot).
- Does removing this affect the No-Show detection or the day-sheet Smart Status Flow (feature 057) shipped earlier? No — those operate on a slot's status (Booked/Appeared/No-Show/Completed), not on whether it was ever reserved capacity; unaffected.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST NOT generate any Fixed-Time slot as reserved/non-directly-bookable — every slot produced by session generation is bookable the same way as every other slot.
- **FR-002**: A doctor's historical no-show rate MUST NOT influence how many slots are produced, or which ones, during session generation.
- **FR-003**: The Day Sheet and any other slot listing MUST NOT display a "reserved capacity" label, or any equivalent messaging implying a slot cannot be booked directly, for any reason tied to this removed mechanism.
- **FR-004**: Walk-in insertion MUST continue to prefer a slot freed by a no-show over any other open slot, exactly as it does today, minus the removed reserved-capacity priority tier that used to be tried first.
- **FR-005**: Walk-in insertion, once no no-show-freed slot is available, MUST fall back to inserting into a regular open slot under the same override-reason requirement already in place today.
- **FR-006**: Removing this mechanism MUST NOT change any other slot-status behavior (No-Show detection, Appeared/Completed transitions, cancellation eligibility) — those are governed entirely by a slot's own status, not by whether it was ever reserved capacity.
- **FR-007**: Existing slots already generated as reserved capacity before this change ships MUST become ordinary, directly bookable slots with no visible distinction from any other slot, requiring no separate migration step for staff or patients to notice or act on.

### Key Entities

- **Slot (Fixed-Time)**: Loses its distinction between "reserved capacity" and "directly bookable" — every slot is simply bookable, subject only to its normal status lifecycle (Open, Booked, Appeared, No-Show, Completed, Cancelled).
- **Walk-in placement**: The ordered set of places a walk-in patient can be placed narrows from three preferences (reserved capacity, then no-show-freed, then regular-with-override) to two (no-show-freed, then regular-with-override).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of newly generated Fixed-Time slots are directly bookable by both patients and staff, with zero slots rejected for being reserved capacity.
- **SC-002**: The Day Sheet and every other slot-facing view show zero occurrences of "reserved capacity" or equivalent non-bookable messaging.
- **SC-003**: Walk-in insertion still succeeds in 100% of cases that succeed today, using its two remaining preference tiers.
- **SC-004**: A doctor's no-show history produces zero measurable difference in the count or spacing of slots generated for their sessions, compared to a doctor with no no-show history and an otherwise identical schedule.

## Assumptions

- This is a pure removal with no replacement mechanism — no new capacity-reservation concept is being introduced to take its place.
- Existing, already-generated reserved-capacity slots simply become ordinary slots the moment this change ships; no explicit data migration, notification, or cleanup step is needed beyond that.
- Walk-in insertion's override-reason requirement for placing a patient into a regular (non-freed) slot is unchanged by this feature — only the removed reserved-capacity tier is dropped from its search order.
- This feature is independent of and does not modify feature 057 (Day Sheet Smart Status Flow), which operates entirely on slot status rather than the reserved-capacity concept.
- No other feature (waitlist, appointment types, doctor risk scoring elsewhere in the product) depends on this mechanism beyond incidental test-fixture usage, based on this session's own review of the codebase before writing this spec.
