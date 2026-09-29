# Feature Specification: Partial (Cutoff-Based) Session Cancellation

**Feature Branch**: `030-partial-cutoff-session-cancellation`

**Created**: 2026-09-04

**Status**: Draft

**Input**: User description: "027 — Partial (Cutoff-Based) Session Cancellation: As staff, I want to cancel only the remaining portion of a session from a certain point onward (e.g. a doctor needs to leave early), so that patients already seen or already past that point aren't affected. Cutoff is defined by time, not token/slot position. Every not-yet-completed slot (OPEN or BOOKED) scheduled at or after the cutoff time is cancelled; slots already COMPLETED, or scheduled before the cutoff, are untouched. Does NOT trigger a waitlist bump — same exclusion as 026. Applies to both fixed-time and queue-mode sessions."

## Clarifications

### Session 2026-09-04

- Q: Should a bare `OPEN` slot (no Booking) at/after the cutoff be left alone, or transitioned into a new terminal "cancelled" Slot state blocking rebooking? → A: Leave it alone — mirrors 026's whole-day cancellation exactly. No new `SlotStatus` value; only `BOOKED` slots' Bookings are actually cancelled by this feature.
- Q: For a Queue-mode Session, what does "scheduled at or after the cutoff time" mean, given a Queue Slot has no `startTime`? → A: Use `Slot.createdAt` (the token-issuance timestamp) as the time proxy, compared against the cutoff time on the Session's own date.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Staff Cancels the Trailing Portion of a Session (Priority: P1) 🎯 MVP

Front-desk Operations staff or the ClinicAdmin cancel every not-yet-completed slot in a Session
from a chosen cutoff time onward — for example, when a doctor needs to leave early — leaving
earlier and already-completed slots untouched.

**Why this priority**: This is the feature's entire purpose; without it there is no
time-partitioned cancellation, only 026's existing all-or-nothing whole-session action.

**Independent Test**: With a Session that has slots both before and after a chosen cutoff time,
some booked and some not-yet-booked, trigger a partial cancellation at that cutoff and confirm
only the at-or-after-cutoff, not-yet-completed slots are affected — with no waitlist bump.

**Acceptance Scenarios**:

1. **Given** a Fixed-Time Session with some slots `COMPLETED`, some `BOOKED`, and some `OPEN`
   spanning both sides of a chosen cutoff time, **When** staff trigger a partial cancellation at
   that cutoff, **Then** every `BOOKED` slot scheduled at or after the cutoff has its Booking
   cancelled and returns to `OPEN`; every already-`OPEN` slot, every slot scheduled before the
   cutoff, and every `COMPLETED` slot regardless of time, is left exactly as it was
   (Clarifications).
2. **Given** a partial cancellation affects Bookings whose Patient has a linked Patient Account,
   **When** the cancellation completes, **Then** each is fed into the notification pipeline
   (036/037), and none triggers a waitlist bump (only 025's individual voluntary cancellation
   ever does).
3. **Given** a cutoff time with nothing scheduled at or after it (every remaining slot is already
   before the cutoff or `COMPLETED`), **When** staff trigger the cancellation, **Then** the action
   reports zero slots affected rather than erroring — a valid, if unusual, cutoff choice.

---

### Edge Cases

- What happens to a slot scheduled exactly at the cutoff time? Included — "at or after" (Business
  Rules) is inclusive of the exact cutoff instant.
- What happens if this action is attempted concurrently with an individual cancellation (025), a
  whole-session cancellation (026), or a walk-in insertion targeting a slot in the affected range?
  Each individual Slot/Booking-level transition is still guarded at the data layer — whichever
  action reaches a given Slot/Booking first wins; the others see it as already resolved.
- What happens to a Queue-mode Slot's "scheduled time" for cutoff comparison, given Queue Slots
  carry no `startTime` in this codebase's data model (only a token number and a creation
  timestamp)? `Slot.createdAt` is used as the comparison proxy (Clarifications).
- What happens to a bare `OPEN` slot at or after the cutoff? Left untouched — nothing exists on
  it to cancel (Clarifications), mirroring 026's identical scope decision.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow an authorized staff member to trigger a partial cancellation
  of a Session at a staff-chosen cutoff time.
- **FR-002**: Every `BOOKED` slot in the Session scheduled at or after the cutoff time MUST have
  its active Booking cancelled, as part of the same action.
- **FR-003**: Only `BOOKED` slots at or after the cutoff have their active Booking cancelled
  (Clarifications) — a bare `OPEN` slot (no Booking) at/after the cutoff is left untouched,
  mirroring 026's identical scope decision; no new `SlotStatus` value is introduced.
- **FR-004**: A partial cancellation MUST NOT alter any slot scheduled before the cutoff time, or
  any slot already `COMPLETED` regardless of its scheduled time.
- **FR-005**: A partial cancellation MUST NOT trigger a waitlist bump for any released slot — the
  same deliberate exclusion 026 already established; only 025's individual voluntary cancellation
  ever triggers one.
- **FR-006**: Each Booking cancelled by this action MUST be recorded for notification purposes
  (036/037) when its Patient has a linked Patient Account, mirroring 026's identical rule.
- **FR-007**: This feature MUST NOT automatically create any waitlist entry or automatically
  rebook any affected patient into a different Session.
- **FR-008**: For a Queue-mode Session, "scheduled at or after the cutoff time" is defined as
  `Slot.createdAt` (the token-issuance timestamp) falling at or after the cutoff time on the
  Session's own date (Clarifications) — the one timestamp a Queue Slot actually carries.
- **FR-009**: The system MUST report the count of slots actually affected by a partial
  cancellation, including zero for a cutoff with nothing qualifying (Edge Cases).

### Key Entities

- **Session** *(existing, from 011)*: The target of this action — its `sessionDate` combines with
  the staff-chosen cutoff `LocalTime` to form the comparison threshold for Fixed-Time Slots.
- **Booking** *(existing, from 016/017/018/025)*: Transitions to `CANCELLED` for every `BOOKED`
  slot at/after the cutoff, via the same guarded mechanism 025/026 already established.
- **Slot** *(existing, from 012/013/019/021/022/025/026)*: A `BOOKED` slot at/after the cutoff
  transitions `→ OPEN` (mirrors 025/026); a bare `OPEN` slot at/after the cutoff is left untouched
  (Clarifications, FR-003). For Queue-mode, `createdAt` stands in for "scheduled time" (FR-008).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every `BOOKED` slot scheduled at or after the chosen cutoff time ends up with its
  Booking cancelled, in the same action.
- **SC-002**: 0 waitlist-bump triggers ever fire as a result of a partial cancellation, regardless
  of how many slots it affects.
- **SC-003**: 100% of slots scheduled before the cutoff, or already `COMPLETED` regardless of
  time, remain completely unchanged by a partial cancellation.
- **SC-004**: A cutoff with nothing qualifying reports zero affected slots rather than an error.
- **SC-005**: Every cancelled Booking whose Patient has a linked Patient Account is fed into the
  notification pipeline exactly once.

## Assumptions

- Authorized the same way as 026's identical write-action gate: an active Operations staff member
  or ClinicAdmin at the Session's clinic, never the Doctor.
- The cutoff is a single `LocalTime` compared against the Session's own date — a Session spans one
  calendar day, so no separate cutoff date is needed.
- This feature ships a minimal staff-facing cancellation action (a cutoff-time input plus the
  cancellation trigger), consistent with prior features' precedent.
