# 016 — Staff-Assisted Fixed-Time Booking

**Module:** Booking
**Status:** Ready for spec-kit intake

## User Story
As front-desk Operations staff (or a ClinicAdmin), I want to book a patient into a specific open Fixed-Time Slot on their behalf, so that patients who call in or walk up without using self-service can still be scheduled.

## Context
Fixed-time clinics offer specific appointment times per patient. Not every patient will self-book (phone bookings, walk-ins converted to a scheduled slot, elderly/less digitally-comfortable patients), so staff need an assisted booking path against the same underlying Slots patients can self-book. BDD §2, §3.2.

## Business Rules
- Staff select an OPEN Slot from an already-generated Fixed-Time Session (see 012-fixed-time-slot-pregeneration) and assign a Patient to it.
- If the Patient does not yet exist as a clinic-scoped record (e.g. a walk-in with no login), staff create that Patient record as part of this flow.
- Fee resolution and locking runs exactly as specified in 015-fee-resolution-and-locking; if no fee can be resolved, the booking is blocked.
- On successful booking, the Slot transitions from OPEN to BOOKED.
- Mobile number is optional on the Patient record for contact-less walk-ins, but if provided must match the Indian numbering plan (10 digits starting 6-9, optional +91/0).

## Acceptance Criteria
- Given an OPEN Fixed-Time Slot and an existing Patient, when staff book that Patient into the Slot, then the Slot becomes BOOKED and a Booking record is created with the resolved, locked fee.
- Given an OPEN Fixed-Time Slot and a new (not-yet-existing) walk-in patient, when staff enter the patient's details and book them, then a new clinic-scoped Patient record is created and the booking proceeds.
- Given no fee can be resolved for the selected appointment type/doctor, when staff attempt to book, then the booking is blocked with a clear error, and the Slot remains OPEN.
- Given a phone number is entered for the walk-in patient, when it does not match the Indian numbering plan, then the booking form rejects it with a validation error; given no phone number is entered at all, the booking is still allowed to proceed.
- Given a Slot is already BOOKED, when staff attempt to book another patient into the same Slot, then the action is rejected.

## Dependencies
- Depends on: 012-fixed-time-slot-pregeneration — Slots must already exist to book against.
- Depends on: 015-fee-resolution-and-locking — every booking must resolve and lock a fee.
- Related: 017-patient-self-service-fixed-time-booking — same underlying Slot/fee mechanics, different actor (patient rather than staff).
- Feeds into: 025-individual-booking-cancellation-waitlist-trigger — this booking can later be cancelled by staff.
- Feeds into: 021-automatic-no-show-detection — this booking is subject to no-show detection once its scheduled time passes.

## Explicitly Out of Scope
- Online payment collection at time of booking (see 015).
- Any reschedule action — moving a booking to a different Slot is done via cancel-then-rebook (see 025, 026, 027), not an atomic reschedule.

## Source References
- BDD §2 (In-Scope: "Staff-assisted and patient self-service booking, with fee snapshotting at booking time")
- BDD §4 (Mobile number validation rule)
