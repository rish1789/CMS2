# Feature Specification: Whole-Day Session Cancellation

**Feature Branch**: `029-whole-day-session-cancellation`

**Created**: 2026-09-04

**Status**: Draft

**Input**: User description: "026 — Whole-Day Session Cancellation: As staff (or in response to a doctor being unavailable), I want to cancel an entire day's session at once, so that every affected booking is handled consistently without cancelling them one by one. Cancels every slot/booking within a full session in one action. Does NOT trigger a waitlist bump for any released slot — only individual voluntary cancellation (025) bumps the waitlist. Applies to both fixed-time and queue-mode sessions. Affected patients' bookings feed the notification pipeline but are not auto-added to any waitlist."

## Clarifications

### Session 2026-09-04

- Q: After whole-session cancellation, do the Session's Slots return to `OPEN` (immediately re-bookable), or does the Session need a new "cancelled" concept blocking every future booking-selection query? → A: Slots return to `OPEN`, mirroring 025's individual-cancellation Slot behavior exactly — a direct, minimal extension of already-converged code. No new Session-level concept; staff handle true day-level unavailability operationally, outside this feature's scope.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Staff Cancels an Entire Session (Priority: P1) 🎯 MVP

Front-desk Operations staff or the ClinicAdmin cancel an entire day's Session in one action — for example, when a doctor calls in sick — so every affected patient's booking is handled consistently without cancelling each one individually.

**Why this priority**: This is the feature's entire purpose; without it there is no whole-session action at all, only 025's existing one-booking-at-a-time path.

**Independent Test**: With a Session (Fixed-Time or Queue-mode) that has several active Bookings, cancel the whole Session as staff and confirm every one of those Bookings is marked cancelled in the same action, with no waitlist bump triggered for any of them.

**Acceptance Scenarios**:

1. **Given** a Fixed-Time Session with several `BOOKED` Slots (active Bookings) and some still-`OPEN` ones, **When** staff cancel the whole Session, **Then** every active Booking in that Session is marked cancelled, and no `BookingCancelledEvent` (025's waitlist-bump trigger) fires for any of them.
2. **Given** a Queue-mode Session with several active Bookings, **When** staff cancel the whole Session, **Then** the same outcome applies — every active Booking cancelled, no waitlist bump.
3. **Given** a Session with zero currently-active Bookings (either because it never had any, or because a prior whole-session cancellation already cancelled them all — Clarifications: there is no separate Session-level "cancelled" flag, so this is the observable definition of "already cancelled"), **When** staff attempt to cancel it, **Then** the action is rejected with a clear "nothing to cancel" outcome rather than silently succeeding with no effect.
4. **Given** a Session with a mix of already-resolved Slots (`NO_SHOW`, `COMPLETED`) alongside active ones, **When** staff cancel the whole Session, **Then** only the active Bookings are cancelled — already-resolved outcomes are left exactly as they were.

---

### Edge Cases

- What happens to a Slot that has no Booking at all (still `OPEN`)? Nothing to cancel there — it's simply part of a now-cancelled Session and is not itself touched by this feature (Clarifications).
- What happens to a walk-in Patient's Booking (no linked Patient Account) when the Session is cancelled? The Booking is still marked cancelled like any other; it is not fed into the notification pipeline (036/037), since that pipeline has no delivery address for a Patient with no linked Account — this is a pre-existing limitation of the notification pipeline itself, not something this feature works around.
- What happens if whole-session cancellation is attempted concurrently with an individual cancellation (025) or a walk-in insertion (025-priority-insertion) targeting a Slot in the same Session? Each individual Booking-level transition is still guarded at the data layer (the same `cancelIfActive` guard 025 already established) — whichever action reaches a given Booking first wins; the other sees it as already resolved.
- What happens to future recurring Sessions generated from the same Schedule? Untouched — this feature cancels one already-generated Session, not the Schedule that produces future ones; nightly generation (011) continues to generate later dates normally.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow an authorized staff member to cancel an entire Session (Fixed-Time or Queue-mode) in one action.
- **FR-002**: A successful whole-session cancellation MUST mark every currently-active Booking within that Session as cancelled, as part of the same action.
- **FR-003**: A whole-session cancellation MUST NOT trigger a waitlist bump for any released Slot — this is an explicit, deliberate exclusion; only 025's individual voluntary cancellation ever triggers one.
- **FR-004**: A whole-session cancellation MUST NOT alter any Slot/Booking that has already reached a resolved outcome (`NO_SHOW`, `COMPLETED`) or is already cancelled.
- **FR-005**: The system MUST reject a whole-session cancellation attempt against a Session with zero currently-active Bookings at that moment — since no separate Session-level "cancelled" flag exists (FR-008), this is the structural definition of "already cancelled" (or "never had anything to cancel"), and is what a repeated cancellation attempt is rejected against.
- **FR-006**: Each Booking cancelled by this action MUST be recorded for notification purposes (036/037) when its Patient has a linked Patient Account; a walk-in Patient with no linked Account is not notified (no delivery address exists for them).
- **FR-007**: This feature MUST NOT automatically create any waitlist entry or automatically rebook any affected patient into a different Session.
- **FR-008**: Every Slot whose active Booking is cancelled by this action MUST return to `OPEN` (Clarifications), exactly mirroring 025's individual-cancellation Slot behavior — no new Session-level "cancelled" concept is introduced; a Slot already `OPEN` (never booked) is left untouched (Edge Cases).

### Key Entities

- **Session** *(existing, from 011)*: The target of this action — cancelling one already-generated Session, not the Schedule that produces future ones.
- **Booking** *(existing, from 016/017/018/025)*: Transitions to `CANCELLED` for every currently-active Booking in the target Session, via the same guarded mechanism 025 established, but without publishing 025's waitlist-bump event.
- **Slot** *(existing, from 012/013/019/021/022/028)*: A Slot whose active Booking is cancelled by this action returns to `OPEN` (Clarifications, FR-008) — the identical transition 025 already performs, just applied to every qualifying Slot in the Session at once.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of currently-active Bookings within a cancelled Session end up cancelled, in the same action that cancelled the Session.
- **SC-002**: 0 waitlist-bump triggers ever fire as a result of a whole-session cancellation, regardless of how many Bookings it cancels.
- **SC-003**: 100% of already-resolved Slot/Booking outcomes (`NO_SHOW`, `COMPLETED`, already-cancelled) within the Session remain unchanged by a whole-session cancellation.
- **SC-004**: 100% of cancellation attempts against a Session with zero currently-active Bookings are rejected, with zero further state change.
- **SC-005**: Every cancelled Booking whose Patient has a linked Patient Account is fed into the notification pipeline exactly once per whole-session cancellation.

## Assumptions

- Authorized the same way as every other staff-initiated booking-lifecycle action in this codebase (016/020/025/026/028): an active Operations staff member or ClinicAdmin at the Session's clinic, never the Doctor.
- A walk-in Patient with no linked Patient Account is silently skipped for notification purposes (FR-006) — 036/037's notification pipeline is account-based only, with no alternative delivery channel for an unlinked Patient anywhere in this codebase; this is a pre-existing boundary of that pipeline, not a new gap this feature introduces.
- This feature ships a minimal staff-facing cancellation action for a whole Session, consistent with prior features' precedent of shipping the actor-facing surface alongside the endpoint that makes it reachable.
