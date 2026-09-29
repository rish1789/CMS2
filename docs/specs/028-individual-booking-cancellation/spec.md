# Feature Specification: Individual Booking Cancellation & Waitlist Trigger

**Feature Branch**: `028-individual-booking-cancellation`

**Created**: 2026-09-04

**Status**: Draft

**Input**: User description: "025 — Individual Booking Cancellation & Waitlist Trigger: As a patient or staff member, I want to cancel an individual fixed-time booking, so that the slot is released and, if a matching patient is waiting, they're automatically offered it. Staff can cancel at any time; a patient can only cancel up to 2 hours before the scheduled slot time (after that, only staff can). This is the ONLY trigger for a waitlist bump in the system — no-show release and whole/partial session cancellations explicitly do not bump the waitlist. Reschedule is not a separate feature — cancel then rebook."

## Clarifications

### Session 2026-09-04

- Q: When a booking is cancelled, is the Booking record kept (marked cancelled, so the Slot can be booked again later by someone new) or deleted (mirroring 025-walk-in-priority-insertion's no-show-slot-reclaim precedent)? → A: Kept — a new `CANCELLED` status on the Booking, distinct from `PaymentStatus`. The Slot becomes `OPEN` and is bookable again by a new Booking; the existing `uq_booking_slot` constraint (one Booking ever per Slot, from 016) becomes a partial/conditional unique index scoped to non-cancelled Bookings, rather than staying flat.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Staff Cancels a Booking (Priority: P1) 🎯 MVP

Front-desk Operations staff or the ClinicAdmin cancel a confirmed fixed-time booking on a patient's behalf, at any time relative to the scheduled slot time.

**Why this priority**: This is the feature's core value and the only cancellation path with no time restriction — without it, nothing else in this feature (including the waitlist trigger) has a way to fire at all.

**Independent Test**: With a confirmed fixed-time Booking, cancel it as staff and confirm the Booking is marked cancelled, its Slot becomes OPEN again, and a waitlist bump trigger fires exactly once.

**Acceptance Scenarios**:

1. **Given** a confirmed fixed-time Booking, **When** staff cancel it, **Then** the Booking is marked cancelled, its Slot becomes OPEN (bookable again by someone new), and a waitlist bump trigger fires exactly once for that Slot.
2. **Given** staff cancel a Booking at any time relative to its scheduled slot time — including less than 2 hours before, or after the scheduled time has already passed — **When** they submit the cancellation, **Then** it succeeds with no time-based restriction (staff are never subject to the patient cutoff).
3. **Given** a Booking that is already cancelled, **When** staff attempt to cancel it again, **Then** the action is rejected (one-way transition).

---

### User Story 2 - Patient Cancels Their Own Booking Within the Cutoff (Priority: P2)

A patient cancels their own confirmed fixed-time booking directly, as long as the scheduled slot time is still at least 2 hours away.

**Why this priority**: Depends on User Story 1's cancellation mechanics and waitlist trigger already existing; this only adds a second, self-service, time-gated access path to the same action.

**Independent Test**: As the patient who holds a confirmed Booking more than 2 hours before its scheduled time, cancel it and confirm the identical outcome User Story 1 produces; attempt the same within 2 hours of the scheduled time and confirm it's blocked with a clear message.

**Acceptance Scenarios**:

1. **Given** a patient's own confirmed fixed-time Booking whose scheduled slot time is more than 2 hours away, **When** they cancel it, **Then** the same outcome as staff-initiated cancellation applies (Booking cancelled, Slot released, waitlist trigger fires once).
2. **Given** a patient's own confirmed fixed-time Booking whose scheduled slot time is less than 2 hours away, **When** they attempt to cancel it, **Then** the action is blocked with a message directing them to contact the clinic — staff can still cancel it on the patient's behalf.
3. **Given** a Booking that is not the requesting patient's own, **When** they attempt to cancel it, **Then** the action is rejected.

---

### Edge Cases

- What happens to a Queue/Token-mode booking? Out of scope — this feature applies only to fixed-time bookings, mirroring 021/025's existing Fixed-Time-only scoping for day-of-operations features; Queue-mode has no equivalent cancellation concept defined in this backlog yet.
- What happens if two cancellation attempts race for the same Booking? Exactly one succeeds in marking it cancelled and firing the waitlist trigger; the other is rejected as already-cancelled — the data layer, not just an application-level check, is the actual guarantee (Constitution IV).
- What happens to the waitlist trigger if nothing is currently listening for it (028/029, the features that own the actual matching/claim logic, are not yet built)? The trigger still fires reliably on every qualifying cancellation — it's a durable signal a later feature consumes, not a live callback that silently does nothing if unheard. Building the matching/claim behavior itself is explicitly out of scope for this feature.
- What happens to a Session Delay figure (023) or Queue Position (024, N/A for fixed-time) when a Booking is cancelled? Out of scope for this feature — this feature only performs the cancellation and fires the waitlist trigger; it does not recalculate delay (021's no-show sweep and 025's walk-in insertion remain the only two defined delay-recalculation triggers).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow an authorized staff member to cancel a confirmed fixed-time Booking at any time, with no restriction relative to the scheduled slot time.
- **FR-002**: The system MUST allow the patient who holds a confirmed fixed-time Booking to cancel it themselves, but only while the scheduled slot time is at least 2 hours away; the system MUST reject a patient's attempt to cancel within that 2-hour cutoff, with a message directing them to contact the clinic.
- **FR-003**: The system MUST reject a cancellation attempt for a Booking that is already cancelled, or whose Slot has already reached a resolved outcome (`NO_SHOW` or `COMPLETED`) — cancellation only applies to a still-confirmed (`BOOKED`) Booking.
- **FR-004**: A successful cancellation MUST mark the Booking `CANCELLED` (retained, not deleted — Clarifications) and MUST return its Slot to OPEN, making it bookable again by a new Booking.
- **FR-004a**: The data layer MUST allow a new Booking to be created against a Slot that already has a `CANCELLED` Booking on it (the existing `uq_booking_slot` constraint becomes scoped to non-cancelled Bookings, not a flat per-Slot constraint — Clarifications), while still preventing two simultaneously-active Bookings on the same Slot.
- **FR-005**: A successful individual booking cancellation MUST fire a waitlist bump trigger exactly once for the released Slot — the sole trigger for waitlist activity anywhere in the system; no other event (no-show release, whole/partial session cancellation) MUST ever fire it.
- **FR-006**: This feature MUST NOT implement waitlist matching or claim logic itself — only a reliable trigger signal a later feature can consume.
- **FR-007**: A patient MUST NOT be able to cancel a Booking that is not their own.
- **FR-008**: Concurrent cancellation attempts against the same Booking MUST result in exactly one succeeding, enforced at the data layer.
- **FR-009**: This feature MUST apply only to fixed-time Bookings; Queue-mode bookings are out of scope.

### Key Entities

- **Booking** *(existing, from 016/017/018/025)*: Gains a new `CANCELLED` status (Clarifications), reachable only from its current confirmed state, distinct from and independent of the existing `PaymentStatus` field.
- **Slot** *(existing, from 012/021/022/026)*: Gains a new reverse transition, `BOOKED → OPEN`, the first time any feature returns a Slot to `OPEN` after it left that state — every prior transition in this codebase has been one-way forward.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of staff-initiated cancellations of a confirmed Booking succeed regardless of how much time remains before the scheduled slot.
- **SC-002**: 100% of patient-initiated cancellation attempts within 2 hours of the scheduled slot time are blocked, with zero state change.
- **SC-003**: Every successful cancellation results in exactly one waitlist bump trigger for the released Slot — never zero, never more than one.
- **SC-004**: A Slot released by cancellation is bookable again by a new Booking, with the original cancelled Booking remaining intact and retrievable.
- **SC-005**: Concurrent cancellation attempts against the same Booking never produce more than one cancelled outcome or more than one waitlist bump trigger.

## Assumptions

- Cancellation is authorized the same way as every other staff-initiated booking-lifecycle action in this codebase (016/020/025/026): an active Operations staff member or ClinicAdmin at the Booking's clinic, never the Doctor — no source material states otherwise for this action specifically.
- Cancellation only applies to a Booking whose Slot is still `BOOKED` — a Slot already `NO_SHOW` or `COMPLETED` has a resolved outcome and can no longer be "cancelled" (mirrors 026's `SlotCompletionService`, which similarly requires `BOOKED` and rejects any other current status).
- The waitlist bump trigger is implemented as a durable, fire-and-forget signal (mirroring this codebase's own 003→008 and 036→037 precedent of publishing an event early for a not-yet-built consumer to pick up later) — this feature's own scope ends at firing it reliably exactly once per qualifying cancellation; it does not define a Waitlist Entry, matching logic, or claim flow, all of which belong to 028/029.
- This feature ships a minimal staff-facing cancellation action and a minimal patient-facing self-service cancellation action, consistent with prior features' precedent of shipping the actor-facing surface alongside the endpoint that makes it reachable.
