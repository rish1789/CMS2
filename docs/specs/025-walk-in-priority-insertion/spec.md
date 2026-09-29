# Feature Specification: Walk-In / Priority Insertion

**Feature Branch**: `025-walk-in-priority-insertion`

**Created**: 2026-09-03

**Status**: Draft

**Input**: User description: "020 — Walk-In / Priority Insertion: As front-desk Operations staff, I want to insert a walk-in patient into an already-running session using a clearly defined priority order (1. reserved buffer slot, 2. a slot freed by an earlier no-show, 3. any other open regular slot with a required written override reason), so that walk-ins are accommodated fairly and predictably. Mobile number optional but validated if given. Fee resolution/locking per 015."

## Clarifications

### Session 2026-09-03

- Q: When a walk-in claims a Slot previously auto-marked NO_SHOW, does the system reuse that same Slot record (removing the original no-show Booking first, since exactly one Booking may ever exist per Slot), or does it leave the original untouched and create a brand-new Slot at the same reclaimed time? → A: Reuse the same Slot record — delete the original no-show Booking first, then book the walk-in into that Slot. This is a deliberate departure from this codebase's otherwise append-only bias elsewhere (e.g. Queue/Token slots), accepted here specifically because the no-show Booking's own history value is considered lower than the operational simplicity of literally reclaiming the same time slot.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Staff Inserts a Walk-In Using a Reserved Buffer Slot (Priority: P1) 🎯 MVP

Front-desk Operations staff insert a walk-in patient into a Session that still has a reserved buffer slot open — the simplest, most common case, requiring no special justification.

**Why this priority**: This is the core value of the feature and the first, most-preferred fallback step — without it, nothing else in the priority order has anywhere to start.

**Independent Test**: With a Session that has an OPEN buffer slot, insert a walk-in and confirm they land in that buffer slot, no override reason required, with a resolved and locked fee.

**Acceptance Scenarios**:

1. **Given** a Session with an OPEN buffer slot, **When** staff insert a walk-in, **Then** the walk-in is placed into that buffer slot, no override reason is required, and a Booking is created with a resolved, locked fee.
2. **Given** a brand-new patient at this clinic is the walk-in, **When** they are inserted, **Then** a new walk-in Patient record is created as part of the same action, exactly as 016's existing walk-in path already does.
3. **Given** no fee can be resolved for the doctor/appointment-type, **When** staff attempt to insert a walk-in, **Then** the insertion is blocked with a clear reason and nothing is created.

---

### User Story 2 - Staff Insert a Walk-In Into a No-Show-Freed Slot (Priority: P2)

With no buffer slot available, staff insert a walk-in into a Session where an earlier booking was auto-marked no-show — still requiring no special justification, since this is the second-preferred, still-legitimate fallback.

**Why this priority**: Depends on User Story 1's insertion mechanics already working; this only changes *which* slot is targeted.

**Independent Test**: With a Session that has no OPEN buffer slot but does have a Slot marked NO_SHOW, insert a walk-in and confirm they claim that reclaimed time, no override reason required.

**Acceptance Scenarios**:

1. **Given** a Session with no OPEN buffer slot but a Slot marked NO_SHOW, **When** staff insert a walk-in, **Then** the original no-show Booking is removed, the walk-in is booked into that same Slot, and no override reason is required (Clarifications).
2. **Given** a Session with both an OPEN buffer slot and a NO_SHOW slot, **When** staff insert a walk-in, **Then** the buffer slot is used, not the no-show-freed one (priority order is strict).

---

### User Story 3 - Staff Insert a Walk-In Into a Regular Slot With a Required Override Reason (Priority: P3)

With neither a buffer slot nor a no-show-freed slot available, staff can still insert a walk-in into any other open regular slot in the Session — but only by providing an explicit written justification, making this the deliberately-harder, last-resort path.

**Why this priority**: This is the feature's guardrail — the least-preferred path, gated specifically so it can never happen silently.

**Independent Test**: With a Session that has neither an OPEN buffer slot nor a NO_SHOW slot, attempt an insertion with no override reason (expect rejection) and then with one supplied (expect success).

**Acceptance Scenarios**:

1. **Given** a Session with no OPEN buffer slot and no NO_SHOW slot, **When** staff attempt to insert a walk-in into any other open regular slot without an override reason, **Then** the insertion is rejected and nothing is created.
2. **Given** the same situation, **When** staff supply a non-blank override reason, **Then** the insertion succeeds into an open regular slot.
3. **Given** an insertion succeeds via this path, **When** the resulting record is later reviewed, **Then** the override reason is retrievable as part of that record (auditability).

---

### Edge Cases

- What happens when a Session has no eligible Slot at all (every Slot is BOOKED, or NO_SHOW ones have already been claimed by an earlier walk-in)? The insertion is rejected with a clear "nothing available" reason.
- What happens when two staff members attempt to insert two different walk-ins into the same Session at nearly the same instant? Each walk-in is independently routed through the same priority search; if both would otherwise land on the identical Slot, the underlying one-booking-per-slot guarantee (016's own proven mechanism) ensures only one succeeds — the loser's request is rejected as "no longer available," the same outcome staff already see today from ordinary concurrent booking attempts.
- What happens to a Session that is not Fixed-Time (a Queue/Token Session)? Out of scope — buffer slots and no-show detection are both already Fixed-Time-only concepts (021, 022), so this feature's priority order has no Queue-mode equivalent; Queue-mode walk-ins are already served by 018's own on-demand token issuance, which needs no priority search at all.
- What happens to a walk-in's mobile number if none is given? Contact-less walk-ins are allowed; the Patient record is created without one, exactly as 016 already supports.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Staff MUST be able to insert a walk-in patient into a Session by having the system search, in strict order, for: (1) an OPEN buffer Slot, (2) a Slot previously marked NO_SHOW, (3) any other OPEN regular Slot — using the first one found and never a later-priority option while an earlier one is available.
- **FR-001a**: A priority-(2) insertion MUST remove the Slot's original no-show Booking and create the walk-in's Booking against that same Slot as a single atomic outcome (Clarifications) — if the insertion fails for any reason (e.g. no fee resolvable), the original no-show Booking MUST remain exactly as it was, never removed without a successful replacement.
- **FR-002**: An insertion using priority (1) or (2) MUST NOT require any override reason.
- **FR-003**: An insertion using priority (3) MUST require a non-blank override reason; the system MUST reject the insertion if none is supplied.
- **FR-004**: A successful priority-(3) insertion's override reason MUST be retained as part of the resulting record.
- **FR-005**: The system MUST resolve and lock a fee for the walk-in's doctor/appointment-type pairing before creating any Booking; if none can be resolved, the insertion MUST be blocked with a clear reason and nothing MUST be created.
- **FR-006**: On a walk-in with no existing Patient record at this clinic, the system MUST create a new clinic-scoped Patient record as part of the same action, exactly as 016's existing staff-assisted booking already does.
- **FR-007**: The walk-in's mobile number MUST be optional; if supplied, it MUST be validated against the Indian numbering plan and rejected if invalid.
- **FR-008**: Only an active Operations staff member or ClinicAdmin at the target clinic MUST be able to perform a walk-in insertion — mirroring 016's existing authorization model exactly.
- **FR-009**: The system MUST reject an insertion attempt against a Session with no eligible Slot at any priority tier, with a clear reason, creating nothing.
- **FR-010**: Concurrent insertion attempts that would otherwise land on the same Slot MUST result in exactly one succeeding — mirroring 016's already-proven one-booking-per-slot race closure.

### Key Entities

- **Slot** *(existing, from 012/021/022)*: The unit searched, in priority order, for a walk-in; its `isBuffer` and `status` (`OPEN`/`NO_SHOW`) fields are what the priority search reads.
- **Booking** *(existing, from 016)*: The record created by a successful insertion; gains a new, optional field to retain the override reason for a priority-(3) insertion (null for priority-(1)/(2) insertions).
- **Patient** *(existing, from 016)*: The walk-in's clinic-scoped record, created or reused exactly as 016 already does.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A walk-in insertion always uses the highest-priority eligible Slot available at the moment of insertion — never a lower-priority one while a higher-priority one exists.
- **SC-002**: 100% of priority-(3) insertion attempts without an override reason are rejected before any record is created.
- **SC-003**: 100% of insertions for which no fee can be resolved are blocked before any Patient or Booking record is created.
- **SC-004**: Every successful priority-(3) insertion's override reason remains retrievable afterward.
- **SC-005**: Concurrent insertion attempts targeting the same eligible Slot never produce more than one successful Booking for it.

## Assumptions

- Reuses 016's staff-assisted booking mechanics (authorization model, fee-resolution-first-write-gate ordering, walk-in Patient creation, race-closure pattern) entirely as-is; this feature only changes how the target Slot is *selected*, not how a Booking is subsequently created against it.
- This feature is Fixed-Time-only — buffer slots (022) and no-show detection (021) are both already Fixed-Time-scoped concepts with no Queue-mode equivalent; Queue-mode walk-ins are already served by 018's own on-demand issuance.
- The override reason is free text (a written justification, not a fixed set of reason codes), consistent with the source doc's "explicit written override reason" phrasing.
- This feature ships a minimal staff-facing frontend form, consistent with the precedent set by 016's own staff-booking form.
