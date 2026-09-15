# 018 — Queue/Token Booking

**Module:** Booking
**Status:** Ready for spec-kit intake

## User Story
As a patient (self-service) or staff member (assisted), I want to book into a Queue/Token Session and receive a token number reflecting my place in the queue, so that I know where I stand without needing a fixed appointment time.

## Context
Queue/Token sessions serve clinics running on a first-come, arrival-order model. Unlike Fixed-Time booking, there is no pre-existing Slot to pick from — booking itself is what creates the Slot and assigns the token. BDD §3.2, §3.3.

## Business Rules
- Booking against a Queue/Token Session triggers creation of exactly one new Slot with the next never-reused token number for that Session (see 013-queue-mode-slot-on-demand-generation).
- Both staff-assisted and patient self-service booking use this same underlying mechanism; the only difference is who initiates it and whether a new walk-in Patient record must be created first.
- Fee resolution and locking runs exactly as specified in 015-fee-resolution-and-locking; if no fee can be resolved, the booking is blocked.
- Queue-mode bookings never carry a delay figure; instead the patient/staff see the booking's live queue position (see 024-queue-position-tracking).
- On a patient self-service booking, first-time-at-this-clinic Patient record creation/linking follows 019-patient-record-auto-creation-phone-linking, identical to the fixed-time self-service path.

## Acceptance Criteria
- Given an active Queue/Token Session, when a patient or staff member books into it, then a new Slot is created with the next unused token number for that Session, and a Booking record is created with a resolved, locked fee.
- Given a Queue/Token booking has just been created, when the patient/staff view it, then it displays a queue-position figure and never a delay-in-minutes figure.
- Given no fee can be resolved for the selected appointment type/doctor, when a booking is attempted, then the booking is blocked with a clear reason.
- Given a staff-assisted queue booking for a walk-in with no existing Patient record, when staff submit the booking, then a new clinic-scoped Patient record is created as part of the same action.
- Given a patient self-service queue booking is their first-ever booking at this clinic, when it completes, then the Patient record is created/linked per 019-patient-record-auto-creation-phone-linking.

## Dependencies
- Depends on: 013-queue-mode-slot-on-demand-generation — this is the Slot-creation mechanism this feature triggers.
- Depends on: 015-fee-resolution-and-locking — every booking must resolve and lock a fee.
- Depends on: 019-patient-record-auto-creation-phone-linking — governs self-service first-booking Patient record handling.
- Feeds into: 024-queue-position-tracking — the created booking is immediately subject to live queue-position calculation.
- Feeds into: 020-walk-in-priority-insertion — walk-ins may also be inserted into a running queue via the priority fallback order (separate from a normal queue booking).

## Explicitly Out of Scope
- Delay-in-minutes tracking — not applicable to queue-mode bookings (fixed-time only, see 023).
- Online payment collection at time of booking (see 015).

## Source References
- BDD §3.2 ("queue-mode sessions create slots one at a time, per booking, with a never-reused token number")
- BDD §3.3 (queue position as the queue-mode equivalent of delay tracking)
