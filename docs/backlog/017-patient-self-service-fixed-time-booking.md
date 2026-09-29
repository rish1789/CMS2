# 017 — Patient Self-Service Fixed-Time Booking

**Module:** Booking
**Status:** Ready for spec-kit intake

## User Story
As a logged-in Patient Account holder, I want to browse open Fixed-Time Slots at a clinic and book one myself, so that I don't need to call or visit in person to get an appointment.

## Context
Patient Account (see 039-patient-account-global-login) gives patients a self-service login. This feature is the booking action available to them once authenticated, mirroring what staff can do on their behalf (016-staff-assisted-fixed-time-booking) but scoped to the logged-in patient's own booking only. BDD §2, §3.2, §5.

## Business Rules
- A Patient Account holder may only book an OPEN Slot; they cannot select or override an already-BOOKED Slot.
- On a patient's first-ever booking at a given clinic, a clinic-scoped Patient record is automatically created for them (not a manual staff step) — see 019-patient-record-auto-creation-phone-linking for the exact linking behavior, including the phone-number match against any prior walk-in record at that clinic.
- Fee resolution and locking runs exactly as specified in 015-fee-resolution-and-locking; if no fee can be resolved, the booking is blocked and the patient is shown a clear reason.
- On successful booking, the Slot transitions from OPEN to BOOKED.
- Patients may only book for themselves (no proxy-booking for another person through this flow in v1).

## Acceptance Criteria
- Given a logged-in Patient Account holder viewing an OPEN Fixed-Time Slot, when they submit a booking, then the Slot becomes BOOKED and a Booking is created under their linked clinic-scoped Patient record, with a resolved, locked fee.
- Given this is the patient's first booking ever at this particular clinic, when the booking completes, then a new clinic-scoped Patient record is created and linked to their Patient Account (or an existing walk-in record is auto-linked by phone match, per 019).
- Given the Slot they attempt to book has just been taken by someone else (race condition), when they submit, then the booking is rejected with a "no longer available" error and no double-booking occurs.
- Given no fee can be resolved for the selected appointment type/doctor, when the patient attempts to book, then the booking is blocked with a clear reason shown to the patient.

## Dependencies
- Depends on: 012-fixed-time-slot-pregeneration — Slots must already exist to book against.
- Depends on: 015-fee-resolution-and-locking — every booking must resolve and lock a fee.
- Depends on: 019-patient-record-auto-creation-phone-linking — governs how the clinic-scoped Patient record is created/linked on first booking.
- Depends on: 039-patient-account-global-login — patient must be authenticated to use this flow.
- Related: 016-staff-assisted-fixed-time-booking — same underlying Slot/fee mechanics, different actor.
- Feeds into: 025-individual-booking-cancellation-waitlist-trigger — patient-initiated cancellation within cutoff is one of the two triggers for a waitlist bump.

## Explicitly Out of Scope
- Online payment collection at time of booking (see 015).
- Proxy-booking on behalf of another person (e.g. a parent booking for a child) — not part of v1.
- Any reschedule action — cancel-then-rebook only (see 025).

## Source References
- BDD §2 (In-Scope: patient self-service booking)
- BDD §3.4 ("patient-initiated within cutoff" as a waitlist-bump trigger, implying self-service cancellation exists alongside self-service booking)
- BDD §5 (Patient Account / Patient relationship)
