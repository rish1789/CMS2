# Feature Specification: Session Delay Tracking (Fixed-Time Only)

**Feature Branch**: `026-session-delay-tracking`

**Created**: 2026-09-04

**Status**: Draft

**Input**: User description: "023 — Session Delay Tracking (Fixed-Time Only): As front-desk staff or a doctor, I want to see how delayed a fixed-time session currently is, so that I can set expectations with waiting patients. Delay is NOT a live timer — it's recalculated at exactly two trigger points: (a) a slot being marked completed, or (b) a walk-in being inserted into the session. Delay figure = minutes between now (at the trigger point) and the scheduled time of the earliest still-unresolved slot (status open/booked, not completed) whose scheduled time has already passed. Applies only to Fixed-Time sessions; Queue-mode sessions never carry a delay figure. No prior feature in this backlog has built any 'mark a Slot/Booking as completed' capability yet — this feature must build that action itself."

## Clarifications

### Session 2026-09-04

- Q: Who should be allowed to mark a Slot as completed — front-desk staff only, or the doctor as well? → A: Staff only — active Operations staff or ClinicAdmin at the clinic (mirrors every prior booking-lifecycle action exactly; doctor excluded).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Staff Marks a Slot Completed, Delay Recalculates (Priority: P1) 🎯 MVP

Front-desk Operations staff (or the ClinicAdmin) mark a booked Slot as completed once that patient's visit has ended. This is the first-ever "completed" action in the system, and it's also the primary trigger that recalculates the Session's delay figure.

**Why this priority**: Without a way to mark a Slot completed at all, neither this feature's core delay-recalculation rule nor its first trigger point can exist — everything else in this feature builds on it.

**Independent Test**: With a Fixed-Time Session where an earlier BOOKED Slot's scheduled time has already passed, mark a later Slot completed and confirm the delay figure recalculates to reflect the earlier, still-unresolved Slot.

**Acceptance Scenarios**:

1. **Given** a Fixed-Time Session with Slots at 9:00, 9:15, 9:30 and the 9:00 Slot is still OPEN/BOOKED at 9:20, **When** staff mark the 9:15 Slot completed, **Then** the Session's delay recalculates as the minutes between now and the 9:00 Slot's scheduled time.
2. **Given** a Slot with no Booking (still OPEN), **When** staff attempt to mark it completed, **Then** the action is rejected — only a BOOKED Slot can be completed.
3. **Given** a Slot already marked completed, **When** staff attempt to mark it completed again, **Then** the action is rejected (one-way transition).
4. **Given** all Slots whose scheduled time has passed are marked completed, **When** the delay recalculates, **Then** the figure reflects no outstanding delay (zero/absent).

---

### User Story 2 - Delay Recalculates When a Walk-In Is Inserted (Priority: P2)

A walk-in insertion (025) into a Fixed-Time Session is the second, independent trigger point that recalculates the same Session's delay figure.

**Why this priority**: Depends on User Story 1's recalculation logic already existing; this only adds the second call site for the same computation.

**Independent Test**: With a Fixed-Time Session where an earlier Slot's scheduled time has already passed, insert a walk-in (025) and confirm the delay figure recalculates immediately as part of that same action.

**Acceptance Scenarios**:

1. **Given** a Fixed-Time Session with an earlier still-unresolved Slot whose scheduled time has passed, **When** a walk-in is inserted into that Session, **Then** the delay figure recalculates as part of the same action.
2. **Given** no Slot is completed and no walk-in is inserted, **When** 20 minutes pass with no trigger, **Then** the displayed delay figure does not change — it is not a live/continuously-updating timer.

---

### User Story 3 - Staff or Doctor Views the Current Delay Figure (Priority: P1)

Front-desk staff or the doctor themselves check a Fixed-Time Session's current delay figure to set expectations with waiting patients.

**Why this priority**: This is the feature's actual point of user value — the recalculation triggers exist only to keep this figure correct when someone looks at it. Tied for P1 with User Story 1 since the two are only independently demonstrable together (a trigger with nothing to view is invisible; a view with no trigger never changes).

**Independent Test**: Query a Fixed-Time Session's delay figure after a trigger point has occurred, and confirm it matches the last-recalculated value; query a Queue-mode Session's delay and confirm none exists.

**Acceptance Scenarios**:

1. **Given** a Fixed-Time Session whose delay was last recalculated to N minutes, **When** staff or the doctor view it, **Then** they see exactly N minutes, regardless of how much wall-clock time has passed since the last trigger.
2. **Given** a Queue-mode Session, **When** its delay is queried, **Then** no delay figure exists — only queue position (024, not yet built) applies to that Session.
3. **Given** a Fixed-Time Session that has never had a trigger point occur, **When** its delay is queried, **Then** it shows no outstanding delay (zero/absent) — not an error.

---

### Edge Cases

- What happens when a Session has no Slots whose scheduled time has passed at all (e.g., queried before the Session starts)? Delay is zero/absent — there is no still-unresolved past-due Slot to measure against.
- What happens when the earliest still-unresolved past-due Slot is itself later marked completed, and another, later Slot is also past due? The next trigger point recalculates against whichever OPEN/BOOKED Slot is now earliest and past due.
- What happens to a Queue-mode Session's "complete a Slot" action? Out of scope for this feature — Queue-mode Slots have no scheduled time to measure delay against in the first place (mirrors 021/022's existing Fixed-Time-only scoping), so this feature's completion action and delay computation apply only to Fixed-Time Sessions.
- What happens if staff mark a Slot completed on a Queue-mode Session? Rejected — this feature's completion action is Fixed-Time-only (mirrors 025's `NotAFixedTimeSessionException`-shaped guard for the same reason).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow an authorized staff member to mark a BOOKED Slot on a Fixed-Time Session as completed; an OPEN Slot (no Booking) MUST be rejected, and an already-completed Slot MUST be rejected (one-way transition).
- **FR-002**: Marking a Slot completed on a Fixed-Time Session MUST recalculate and store that Session's delay figure as part of the same action.
- **FR-003**: A walk-in insertion (025) into a Fixed-Time Session MUST recalculate and store that Session's delay figure as part of the same action.
- **FR-004**: The delay figure MUST equal the number of minutes between the trigger point's "now" and the scheduled time of the earliest Slot in that Session whose status is OPEN or BOOKED (not completed) and whose scheduled time has already passed as of that trigger point; if no such Slot exists, the delay figure MUST be zero/absent.
- **FR-005**: The delay figure MUST NOT change between trigger points, regardless of how much wall-clock time passes — it is read from stored state, never recomputed live at query time.
- **FR-006**: The system MUST allow staff or the doctor to view a Fixed-Time Session's current (last-recalculated) delay figure.
- **FR-007**: A Queue-mode Session MUST NEVER carry a delay figure; querying one MUST indicate no delay figure exists, not an error and not zero-as-a-computed-value.
- **FR-008**: Marking a Slot completed MUST be rejected for a Queue-mode Session's Slot — this feature's completion action applies only to Fixed-Time Sessions.
- **FR-009**: Only an active Operations staff member or ClinicAdmin at the Slot's clinic MUST be able to mark a Slot completed — mirroring 016/020/025's existing staff-action authorization model exactly (never the Doctor, consistent with every prior booking-lifecycle action in this codebase) (Clarifications).

### Key Entities

- **Slot** *(existing, from 012/021/022/025)*: Gains a new `COMPLETED` status value, reached only from `BOOKED`.
- **Session** *(existing, from 011)*: Gains a stored delay figure (nullable/absent = no outstanding delay), recalculated only at the two defined trigger points.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After either trigger point occurs, the Session's delay figure always reflects the correct minutes-past-due of the earliest still-unresolved past-due Slot at that moment.
- **SC-002**: The delay figure never changes outside of the two defined trigger points, even when significant wall-clock time (20+ minutes) passes with no trigger.
- **SC-003**: 100% of attempts to mark an OPEN Slot, an already-completed Slot, or a Queue-mode Session's Slot as completed are rejected, with no state change.
- **SC-004**: A Queue-mode Session's delay query always indicates no delay figure exists, never a numeric value.
- **SC-005**: Once every past-due Slot in a Session is completed, its delay figure reflects zero/no outstanding delay at the very next trigger point.

## Assumptions

- Reuses 016/020/025's existing "active Operations staff or ClinicAdmin at the clinic, never the Doctor" authorization model for the new "mark Slot completed" action (Clarifications) — no prior feature in this backlog had built a completion action before now, so this keeps a single consistent authorization shape across every staff-initiated Slot/Booking-lifecycle action rather than introducing a new one.
- The delay figure is stored (cached) on the Session at trigger time, not recomputed on read — this is what "stale-but-correct-as-of-last-trigger by design" and "no background recalculation" in the source material require; a query endpoint simply reads the last-stored value.
- Only a BOOKED Slot can be marked completed; an OPEN Slot has no visit to complete. No consultation-note or clinical-documentation requirement is attached to completion — that remains 030's separate concern (Explicitly Out of Scope).
- This feature is Fixed-Time-only throughout (completion action and delay figure both) — Queue-mode Sessions are served by 024's queue-position concept instead, per the source material's explicit mutual-exclusion note.
- Completion is a one-way transition (no un-completing), consistent with this codebase's other one-way Slot status transitions (`BOOKED`, `NO_SHOW`).
- This feature ships a minimal staff-facing frontend affordance (a "mark completed" action and a delay display), consistent with 016/020/025's precedent of shipping a form/view alongside the first feature that makes it reachable.
