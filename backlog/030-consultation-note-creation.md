# 030 — Consultation Note Creation

**Module:** Clinical Documentation
**Status:** Ready for spec-kit intake

## User Story
As a treating doctor, I want to write a consultation note for a booking I personally handled, so that there's a permanent clinical record of the visit.

## Context
Clinical documentation in this system is deliberately lightweight and immutable rather than a full editable EMR — this is stricter than "one per booking," it's genuinely write-once. BDD §3.5.

## Business Rules
- Strictly immutable once created — there is no edit function anywhere. A correction requires creating a fresh note on a new visit/booking, not modifying the original.
- Only the treating doctor for that specific booking may write documentation for it.
- Authorization is traced by following the booking back to its slot's assigned doctor — the acting doctor's identity must match the doctor assigned to that slot.
- No clinic-ownership override exists: a ClinicAdmin or a different doctor at the same clinic cannot write, edit, or override this note on the treating doctor's behalf under any circumstance.
- One of three independent, identically-scoped clinical documentation types alongside Prescription + Items (031) and External Record References (032) — each has its own immutability and doctor-authorization rule, applied the same way.

## Acceptance Criteria
- Given a booking with an assigned treating doctor, when that doctor creates a consultation note for that booking, then the note is saved and permanently locked against edits.
- Given a consultation note already exists for a booking, when anyone — including the original authoring doctor — attempts to edit it, then the system rejects the edit; no update path exists.
- Given a doctor who is NOT the treating doctor for a booking (including a ClinicAdmin at the same clinic), when they attempt to create or edit a note for that booking, then the system rejects the action.
- Given a patient needs a correction to prior documentation, when the doctor wants to record it, then they must create a brand-new consultation note on a new visit/booking — the old note remains unchanged.

## Dependencies
- Depends on: 016-staff-assisted-fixed-time-booking, 017-patient-self-service-fixed-time-booking, 018-queue-token-booking — a booking with an assigned treating doctor must exist
- Related to (same immutability/authorization rules, separate entity): 031-prescription-and-items-creation, 032-external-record-reference

## Explicitly Out of Scope
- Any edit or versioning capability for consultation notes.
- Any clinic-admin or peer-doctor override of doctor-only write access.

## Source References
- BDD §3.5 (Clinical Documentation: immutability and doctor-only authorization rules)
