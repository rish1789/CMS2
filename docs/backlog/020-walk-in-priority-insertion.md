# 020 — Walk-In / Priority Insertion

**Module:** Scheduling & Session Generation *(Day-of Operations)*
**Status:** Ready for spec-kit intake

## User Story
As front-desk Operations staff, I want to insert a walk-in patient into an already-running session using a clearly defined priority order, so that walk-ins are accommodated fairly and predictably without staff having to guess which slot is safe to give away.

## Context
Real clinic days involve people showing up without a booking. The system needs a deterministic way to decide which Slot a walk-in gets, without silently bumping a patient who already has a confirmed appointment. BDD §3.3, §6 confirms this ordering was previously ambiguous and is now fully specified.

## Business Rules
- Walk-in insertion follows a strict, ordered fallback sequence — staff/system must attempt each step in order and only fall through to the next if the prior one is unavailable:
  1. A reserved buffer slot (see 022-buffer-slot-capacity-sizing) for that Session, if one is currently OPEN.
  2. A slot that was freed by an earlier no-show release (see 021-automatic-no-show-detection) for that Session, if one is currently OPEN.
  3. Any other open regular Slot in that Session — but ONLY when the inserting staff member provides an explicit written override reason. This step cannot be used silently; the override reason is a required field, not optional.
- Mobile number is optional on the walk-in's Patient record (contact-less walk-ins are allowed), but if provided must match the Indian numbering plan.
- If the walk-in is a new patient at this clinic, a walk-in Patient record is created as part of this flow (no login, staff-entered).
- Fee resolution and locking runs exactly as specified in 015-fee-resolution-and-locking.

## Acceptance Criteria
- Given a Session with an OPEN buffer slot, when staff insert a walk-in, then the walk-in is placed into that buffer slot without requiring an override reason.
- Given a Session with no OPEN buffer slot but a slot freed by an earlier no-show is OPEN, when staff insert a walk-in, then the walk-in is placed into that no-show-freed slot without requiring an override reason.
- Given a Session with no OPEN buffer slot and no no-show-freed slot available, when staff attempt to insert a walk-in into any other open regular slot, then the system requires a written override reason before the insertion is allowed to proceed.
- Given staff attempt to insert a walk-in into a regular open slot without providing an override reason, when they submit, then the insertion is rejected until a reason is supplied.
- Given a walk-in is a brand-new patient at this clinic, when they are inserted, then a new walk-in Patient record is created as part of the same action.

## Dependencies
- Depends on: 022-buffer-slot-capacity-sizing — defines step 1 of the fallback order.
- Depends on: 021-automatic-no-show-detection — defines step 2 of the fallback order (slots it frees become eligible here).
- Depends on: 015-fee-resolution-and-locking — every walk-in booking must resolve and lock a fee.
- Feeds into: 023-session-delay-tracking — a walk-in insertion is one of only two triggers that recalculate delay for fixed-time sessions.

## Explicitly Out of Scope
- Automatic assignment without staff involvement — this is always a staff-initiated action in v1, not a self-service walk-in flow.

## Source References
- BDD §3.3 ("Walk-in insertion priority order is now fully specified: (1) reserved buffer slot, (2) a slot freed by an earlier no-show, (3) any other open regular slot — but only with an explicit written override reason")
- BDD §4 (Mobile number validation, optional for contact-less walk-ins)
- BDD §6.3 (walk-in insertion as a delay-recalculation trigger)
