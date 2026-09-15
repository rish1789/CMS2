# 012 — Fixed-Time Session Slot Pre-Generation

**Module:** Scheduling & Session Generation
**Status:** Ready for spec-kit intake

## User Story
As the System (Scheduler), I want every Slot of a Fixed-Time Session to be pre-generated at the moment the Session itself is generated, so that staff and patients see a complete, bookable timetable for that day immediately rather than slots appearing incrementally.

## Context
Fixed-Time sessions represent clinics that run on a scheduled-appointment model (specific time per patient), as opposed to Queue/Token sessions which are walk-up/first-come style. BDD §3.2 draws a hard distinction in how the two modes create their Slots. This feature covers only the Fixed-Time path.

## Business Rules
- When a Fixed-Time Session is generated (see 011-nightly-rolling-session-generation), every Slot for that Session is created in the same step — all slots exist up front, not created lazily on booking.
- Slot count and spacing are derived from the parent Recurring Schedule's configuration (time window, per-patient duration).
- Each pre-generated Slot starts in an OPEN state, available to be booked.
- Buffer slots reserved per the buffer-sizing formula (see 022-buffer-slot-capacity-sizing) are part of this pre-generation step, spread evenly through the day rather than clustered at the end.
- Pre-generated Slots are not affected by later edits to the Recurring Schedule (see 014-schedule-edit-non-retroactivity).

## Acceptance Criteria
- Given a Fixed-Time Recurring Schedule configured for a 9am-1pm window with a defined per-patient duration, when its Session is generated for a given date, then all Slots for that time window are created immediately, each in OPEN state.
- Given the buffer-sizing formula calculates N buffer slots for this Session, when Slots are pre-generated, then N buffer slots are included and distributed evenly across the day's Slot sequence, not appended only at the end.
- Given a Fixed-Time Session whose Slots have already been pre-generated, when a booking is later made against one of those Slots, then no new Slot is created — the booking attaches to an existing pre-generated Slot.
- Given a Fixed-Time Session with all Slots pre-generated, when a patient or staff member views that day's schedule, then every Slot (including buffer slots) is visible before any booking occurs.

## Dependencies
- Depends on: 011-nightly-rolling-session-generation — this pre-generation happens as part of that job's Fixed-Time path.
- Depends on: 022-buffer-slot-capacity-sizing — determines how many of the pre-generated Slots are reserved as buffer.
- Blocks / feeds into: 016-staff-assisted-fixed-time-booking, 017-patient-self-service-fixed-time-booking — booking selects from these pre-generated Slots.
- Blocks / feeds into: 023-session-delay-tracking — delay calculation walks these pre-generated, time-stamped Slots.

## Explicitly Out of Scope
- Queue/Token slot creation — see 013-queue-mode-slot-on-demand-generation for the opposite (on-demand) model.
- Session-level live status tracking — not part of v1 (slot-level status only).

## Source References
- BDD §3.2 ("Fixed-time sessions pre-generate every slot at generation time")
- BDD §4 (buffer capacity sizing formula, spread evenly through the day)
