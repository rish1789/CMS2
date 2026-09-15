# Feature Specification: Automatic No-Show Detection

**Feature Branch**: `023-no-show-detection`

**Created**: 2026-09-03

**Status**: Draft

**Input**: User description: "021 — Automatic No-Show Detection: As the scheduling system, I want to automatically mark a fixed-time booking as no-show after a 10-minute grace period past its scheduled time, so that slot status reflects reality, no-show history is captured for buffer sizing, and unattended slots become eligible for walk-in reclaim. Skipped if the slot is on an explicit hold. Never triggers a waitlist bump."

## Clarifications

### Session 2026-09-03

- Q: Should this feature apply only to Fixed-Time bookings, or should Queue/Token bookings also get an equivalent automatic timeout/no-show rule? → A: Fixed-Time only. Queue/Token Slots have no individual scheduled time to measure a grace period against; a future feature would need to define what "no-show" means for queue mode before this logic could extend there.
- Q: Should this feature introduce a minimal, currently-unset "hold" flag on a Slot, or should the hold-check be dropped entirely for v1 since nothing in this backlog can currently set it? → A: Add a currently-unset `onHold` flag now, with no UI/endpoint to set it yet — matches this session's established pattern for infrastructure a stated business rule requires ahead of the feature that would set it.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - System Auto-Marks an Unattended Booking as No-Show (Priority: P1) 🎯 MVP

Without any staff action, the system detects that a confirmed Fixed-Time booking's scheduled time plus a 10-minute grace period has passed with no indication the patient was seen, and marks that Slot NO_SHOW.

**Why this priority**: This is the entire feature — everything else (buffer-sizing input, walk-in reclaim eligibility) depends on this detection actually happening.

**Independent Test**: Create a Fixed-Time booking for a time now more than 10 minutes in the past, run the detection sweep, and confirm the Slot is now NO_SHOW.

**Acceptance Scenarios**:

1. **Given** a Fixed-Time Slot booked for a scheduled time now more than 10 minutes in the past, **When** the detection sweep runs, **Then** the Slot is marked NO_SHOW.
2. **Given** a Fixed-Time Slot booked for a scheduled time less than 10 minutes in the past, **When** the detection sweep runs, **Then** the Slot remains BOOKED (grace period not yet elapsed).
3. **Given** an OPEN Slot with no Booking, **When** the detection sweep runs, **Then** it is left untouched (nothing to mark no-show).
4. **Given** a Slot already marked NO_SHOW, **When** the detection sweep runs again, **Then** it is left untouched (already marked, not re-processed).

---

### User Story 2 - No-Show Never Triggers a Waitlist Bump and Stays Manually Reclaimable (Priority: P2)

A staff member (or a future feature) checking on a no-show Slot confirms the system did not automatically reassign or bump anyone into it — it simply sits available for a manual walk-in reclaim.

**Why this priority**: Confirms the feature's explicit non-side-effect boundary — a lower-severity concern than the detection itself, but important to verify since a wrong default here (auto-bumping the waitlist) would be a real regression against feature 025's exclusive ownership of that trigger.

**Independent Test**: Mark a Slot NO_SHOW via the sweep, and confirm no waitlist-related record or event is created as a side effect.

**Acceptance Scenarios**:

1. **Given** a Slot has just been auto-marked NO_SHOW, **When** any waitlist-related process would otherwise check for a trigger, **Then** none is found — no bump occurs as a consequence of this feature.

---

### Edge Cases

- What happens to a Slot that is on an explicit hold and past its grace period? It is left untouched — never auto-marked NO_SHOW while held.
- What happens if the detection sweep runs while a booking's fee resolution or patient linking is mid-flight (a race with 016/017's own booking creation)? Out of scope for this feature to reason about at the request level — the sweep only ever acts on Slots that are already durably `BOOKED` at the moment it queries; a booking still in progress simply hasn't reached that state yet and is not a candidate.
- What happens to a Queue/Token booking whose token has been waiting a long time? Nothing — per Clarifications, this feature is scoped to Fixed-Time only.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST, without requiring staff action, detect a Fixed-Time Slot that is currently `BOOKED`, not on hold, and whose scheduled time is more than 10 minutes in the past, and mark it `NO_SHOW`.
- **FR-002**: The system MUST NOT mark a Slot `NO_SHOW` while that Slot is on an explicit hold, regardless of how much time has passed. Per Clarifications, this feature introduces the currently-unset `onHold` flag itself (no UI/endpoint to set it yet — infrastructure ahead of the feature that will).
- **FR-003**: The system MUST NOT act on a Slot that is not currently `BOOKED` (e.g. already `OPEN`, or already `NO_SHOW`) — each Slot is only ever auto-marked once.
- **FR-004**: The system MUST NOT automatically reassign, rebook, or bump a waitlist entry as a consequence of marking a Slot `NO_SHOW`; the Slot simply becomes eligible for a future feature's manual walk-in reclaim.
- **FR-005**: A Slot's `NO_SHOW` history MUST be queryable per-patient, scoped by date, so that a future feature can compute a trailing-window count (e.g. "how many no-shows did this patient have in the last 90 days") without this feature needing to compute or store that aggregate itself.
- **FR-006**: This feature applies only to Fixed-Time Slots; Queue/Token Slots are explicitly out of scope (Clarifications).
- **FR-007**: The detection sweep MUST run automatically and periodically, without requiring a manual trigger for normal operation.

### Key Entities

- **Slot** *(existing, from 012/016)*: Gains a new terminal status this feature is responsible for setting: `NO_SHOW`, reached only from `BOOKED`. Also gains a currently-unset `onHold` flag (Clarifications) that, when set, exempts the Slot from this feature's detection entirely — no feature in this backlog yet provides a way to set it.
- **Booking** *(existing, from 016)*: Unchanged; its `patient` and `slot` associations are what makes a `NO_SHOW` Slot's history attributable to a specific patient for a future trailing-window query (FR-005).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every Fixed-Time booking whose scheduled time plus 10-minute grace period has elapsed, with no hold and no other status change, is marked `NO_SHOW` without any staff action, within one sweep cycle.
- **SC-002**: 100% of held Slots remain unmarked regardless of elapsed time.
- **SC-003**: 0 waitlist-related side effects are ever produced by this feature's own detection sweep.
- **SC-004**: A Slot is never processed more than once by the sweep (no double-marking, no re-marking of an already-`NO_SHOW` Slot).

## Assumptions

- Reuses 012/016's `Slot`/`SlotStatus` model, extending it in place with `NO_SHOW` (mirroring how `BOOKED` itself was added), rather than introducing a separate `Booking`-level status — the acceptance criteria and business rules consistently describe the Slot itself as what changes state ("marks the slot NO_SHOW," "slot status reflects reality").
- No new field is added to `Patient` for a running no-show count; a trailing-window count is something a future feature computes by querying `NO_SHOW` Slots joined to their Booking's patient, filtered by date — a plain incrementing counter could not correctly support a *trailing* (expiring) window in the first place.
- "The patient has not been marked arrived/completed" (source doc wording) has no equivalent status anywhere in this system yet, since no check-in feature exists in this backlog; for this feature's purposes, a Slot still simply `BOOKED` past the grace period is exactly the condition this represents — nothing currently transitions a Slot away from `BOOKED` other than this feature itself (to `NO_SHOW`).
- The detection mechanism runs on a periodic, automatic basis (not on-demand per request), consistent with the existing nightly session-generation and notification-expiry background-job patterns already in this codebase.
- No new endpoint or UI ships with this feature — it is a fully automatic background process with no manual trigger required for correct operation (a manual re-run capability, if any, is an implementation-level convenience, not a requirement).
