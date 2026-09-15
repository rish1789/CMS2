# Feature Specification: Patient Immediate Anonymization

**Feature Branch**: `037-patient-immediate-anonymization`

**Created**: 2026-09-04

**Status**: Draft

**Input**: User description: "033 — Patient Immediate Anonymization: As a ClinicAdmin (or authorized staff member), I want to immediately anonymize a patient's identifying information on request, so that the clinic can honor a data-deletion/erasure request under DPDP without losing the underlying booking/clinical history structure. Replaces the patient's name with a placeholder and clears all other identifying/contact fields. Blocked if any active future booking exists. Acts on the clinic-scoped Patient record, not the global Patient Account. Does not delete the Patient record or any bookings/clinical content."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Staff Anonymizes a Patient Record on Request (Priority: P1) 🎯 MVP

An authorized staff member honors a patient's data-erasure request by immediately scrubbing that
patient's identifying information at this clinic, while everything the clinic needs to keep
functioning — the booking history, the clinical documentation trail — stays structurally intact,
just no longer traceable to that person's name or contact details.

**Why this priority**: The feature's entire named purpose — the first of DPDP's two-tier deletion
model this system implements.

**Independent Test**: With a Patient record that has no active future bookings, trigger
anonymization and confirm the name is replaced with a placeholder, the phone number is cleared,
and the record is now marked anonymized — while their historical bookings and clinical
documentation remain fully intact and still reference that Patient record.

**Acceptance Scenarios**:

1. **Given** a Patient record with no active future bookings, **When** an authorized staff member
   triggers anonymization for it, **Then** the name is replaced with a placeholder, the phone
   number is cleared, the record is marked anonymized, and the change is immediate.
2. **Given** a Patient record with at least one active future booking, **When** anonymization is
   attempted, **Then** the system rejects the action and modifies no fields.
3. **Given** a patient has been anonymized, **When** their historical bookings and clinical
   documentation (Consultation Notes, Prescriptions, External Record References) are viewed,
   **Then** those records remain fully intact and still reference the now-anonymized Patient
   record — only the Patient record's own identifying fields were touched.
4. **Given** a patient's future booking that was blocking anonymization is later cancelled,
   **When** anonymization is retried, **Then** it now succeeds.
5. **Given** a patient has already been anonymized, **When** anonymization is attempted again,
   **Then** the system treats it as already done — no error, no change to the original
   anonymization timestamp, and the fields remain scrubbed (idempotent).

---

### Edge Cases

- What happens if someone tries to anonymize a Patient record that doesn't exist at this clinic?
  Rejected — not found.
- What happens to the linked Patient Account (the global login identity, if this Patient record
  is linked to one) when the Patient record is anonymized? Left untouched — anonymization acts
  only on the clinic-scoped Patient record's own identifying fields (name, phone), not on the
  separate global Patient Account record, which this feature doesn't touch at all.
- What counts as "active future"? A booking whose Slot has not yet reached a resolved outcome
  (still `BOOKED`, not yet `NO_SHOW`/`COMPLETED`/cancelled) — mirroring this system's own existing
  definition used by 008/033's de-verification cascade for the identical concept.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow an authorized staff member to trigger anonymization for a
  clinic-scoped Patient record.
- **FR-002**: Anonymization MUST replace the Patient's name with a fixed placeholder value and
  clear its phone number — every identifying/contact field the Patient record actually has.
- **FR-003**: The system MUST reject an anonymization attempt while the Patient has at least one
  active future booking, modifying no fields when it does.
- **FR-004**: Anonymization MUST record a durable, queryable "anonymized" state on the Patient
  record, so a later feature (034) can check it as a precondition without inferring it from field
  contents.
- **FR-005**: Anonymization MUST NOT delete the Patient record, any Booking, or any clinical
  documentation — only the Patient record's own identifying fields are modified.
- **FR-006**: Anonymization MUST NOT alter the Patient record's link to its Patient Account (if
  any) or any field on the Patient Account record itself.
- **FR-007**: A repeated anonymization attempt against an already-anonymized Patient MUST succeed
  as a no-op — no error, no change to the original anonymization timestamp.
- **FR-008**: Anonymization MUST take effect immediately upon a successful request — never
  queued or deferred.

### Key Entities

- **Patient** *(existing, 009)*: Gains a new durable "anonymized" marker (FR-004) and has its
  `name`/`phone` fields cleared by this action — the only entity this feature writes to.
- **Booking** *(existing, 016/017/018/025/026/027)*: Read-only for this feature — its current
  state determines whether anonymization is currently blocked (FR-003); nothing about it is
  written by this feature.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of anonymization attempts against a Patient with no active future bookings
  succeed immediately, with the name and phone number cleared.
- **SC-002**: 100% of anonymization attempts against a Patient with an active future booking are
  rejected, with zero fields modified.
- **SC-003**: 100% of an anonymized Patient's historical bookings and clinical documentation
  remain fully intact and queryable afterward.
- **SC-004**: 100% of repeated anonymization attempts against an already-anonymized Patient
  succeed as a no-op, with the original anonymization timestamp unchanged.

## Assumptions

- The `Patient` record's only actual identifying/contact fields, as built by this codebase
  (009-patient-record-phone-linking), are `name` and `phone` — no `address`, emergency contact,
  or safety/allergy-form field exists anywhere in this system. The source material's broader
  illustrative list ("phone number, address, emergency contact, safety-form text") describes the
  *category* of data to scrub, not a literal field checklist; only the fields that actually exist
  are cleared.
- The new durable "anonymized" marker is a nullable `anonymizedAt` timestamp — doubling as both
  the boolean flag 034 needs to check and an audit record of when the action happened, set once
  and never changed afterward (FR-007).
- Authorization uses this codebase's own established Operations-or-ClinicAdmin write-action gate
  (the same one 016/020/025/029/030's own staff-write actions already use) — the source
  material's "ClinicAdmin (or authorized staff member)" phrasing doesn't name a different gate,
  and this mirrors the session's own consistent precedent rather than introducing a new one.
- This feature is staff-initiated only — the source material describes no patient self-service
  trigger, and its own Explicitly Out of Scope section confirms this.
- No cross-clinic anonymization — this acts on exactly one clinic-scoped Patient record per
  request, consistent with the system's clinic-scoped patient model.
