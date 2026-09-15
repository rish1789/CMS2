# Feature Specification: Consultation Note Creation

**Feature Branch**: `034-consultation-note-creation`

**Created**: 2026-09-04

**Status**: Draft

**Input**: User description: "030 — Consultation Note Creation: As a treating doctor, I want to write a consultation note for a booking I personally handled, so that there's a permanent clinical record of the visit. Strictly immutable once created — there is no edit function anywhere; a correction requires creating a fresh note on a new visit/booking. Only the treating doctor for that specific booking may write documentation for it, traced by following the booking back to its slot's assigned doctor. No clinic-ownership override exists. Exactly one consultation note per booking."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Treating Doctor Writes a Consultation Note (Priority: P1) 🎯 MVP

The doctor who actually saw a patient for a given booking writes a permanent clinical note for
that visit, so there's a lasting record of what happened — and once written, it can never be
altered by anyone, including the doctor who wrote it.

**Why this priority**: The feature's entire named purpose, and the first feature to materialize
the constitution's own named "clinical documentation" module.

**Independent Test**: As the doctor assigned to a booking's slot, create a consultation note for
it, then confirm it's retrievable and that no edit action exists anywhere for it.

**Acceptance Scenarios**:

1. **Given** a booking with an assigned treating doctor, **When** that doctor creates a
   consultation note for it, **Then** the note is saved and permanently locked against edits.
2. **Given** a consultation note already exists for a booking, **When** anyone — including the
   original authoring doctor — attempts to edit it, **Then** the system rejects it; no update
   action exists for this at all.
3. **Given** a consultation note already exists for a booking, **When** the treating doctor
   attempts to create a second one for that same booking, **Then** the system rejects it —
   exactly one note per booking, ever.
4. **Given** a doctor who is NOT the treating doctor for a booking — including a ClinicAdmin at
   the same clinic — **When** they attempt to create a note for it, **Then** the system rejects
   the action; no clinic-ownership or peer-doctor override exists under any circumstance.
5. **Given** a patient needs a correction to prior documentation, **When** the doctor wants to
   record it, **Then** they create a brand-new consultation note on a new visit/booking — the old
   note remains permanently unchanged.
6. **Given** a consultation note exists for a booking, **When** the treating doctor retrieves it,
   **Then** they see its content — a permanent clinical record has to actually be readable to
   serve its stated purpose.

---

### Edge Cases

- What happens if someone tries to write a note for a booking that doesn't exist? Rejected — not
  found.
- What happens if a doctor tries to write a note for a booking at a clinic they're no longer
  actively staffed at? Out of scope to specifically re-check staffing status here — authorization
  is solely "does the acting doctor's identity match the booking's treating doctor," per the
  source material's own stated rule; that identity match doesn't depend on any separate,
  currently-active staffing check.
- What happens if the booking's slot has been cancelled or the patient never showed up? No
  precondition on the booking's or slot's status is stated anywhere in the source material — a
  note can be created as long as the booking exists and the acting doctor is its treating doctor.
- Who besides the treating doctor can ever read a note? Out of scope for this feature to define
  broader clinic-wide or patient-facing read access — only the treating doctor's own read path is
  in scope here (Assumptions).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow the doctor assigned to a booking's slot (its "treating
  doctor") to create exactly one consultation note for that booking.
- **FR-002**: A consultation note MUST be permanently immutable once created — no update or
  delete action MUST exist for it anywhere in the system.
- **FR-003**: The system MUST reject a second consultation note creation attempt against a
  booking that already has one.
- **FR-004**: The system MUST reject a consultation note creation attempt by anyone other than
  the specific booking's treating doctor — including a ClinicAdmin at the same clinic, or a
  different doctor — with no override path of any kind.
- **FR-005**: Authorization MUST be determined solely by tracing the booking to its slot's
  assigned doctor and comparing that to the acting doctor's own identity — no separate
  clinic-staffing-status check is required.
- **FR-006**: The system MUST allow the treating doctor to retrieve the consultation note for a
  booking they authored.
- **FR-007**: The system MUST NOT impose any precondition on the booking's or its slot's status
  (e.g. completed, cancelled) before a note may be created.

### Key Entities

- **ConsultationNote** *(new)*: A permanent, immutable clinical record for exactly one Booking.
  Fields: the Booking it belongs to, the authoring doctor, its content, and its creation
  timestamp. First feature to materialize the constitution's own named "clinical documentation"
  module.
- **Booking** *(existing, 016/017/018)*: Read-only for this feature — supplies the treating
  doctor (via its Slot's Session) that authorization is traced against; nothing about the Booking
  itself is written by this feature.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of consultation note creation attempts by a booking's actual treating doctor,
  where none yet exists, succeed and are permanently retrievable afterward.
- **SC-002**: 100% of consultation note creation attempts by anyone other than the treating
  doctor are rejected, with zero exceptions or overrides.
- **SC-003**: 100% of attempts to create a second note for an already-documented booking are
  rejected.
- **SC-004**: 0% of consultation notes are ever altered or removed after creation — no code path
  exists that could do so.

## Assumptions

- A consultation note's content is a single free-text field — the source material describes "a
  permanent clinical record of the visit" without specifying any structured sub-fields, and this
  feature's siblings (031-prescription-and-items-creation, 032-external-record-reference) are
  explicitly separate, differently-shaped entities for the more structured clinical data that
  might otherwise seem to belong here.
- Exactly one Consultation Note may ever exist per Booking, enforced at the data layer (a
  uniqueness constraint) per Constitution IV — not just an application-level check — mirroring
  this codebase's now-consistent race-closure pattern for every other "at most one X per Y"
  invariant.
- Only the treating doctor's own read access is in scope — this feature does not define any
  broader staff/ClinicAdmin/patient-facing read surface; a future feature can extend read access
  if that's ever a stated requirement.
- `ConsultationNote` and this feature's service/controller live in a new `com.cms.clinical`
  module — the constitution's own explicitly named "clinical documentation" module boundary,
  materializing for the first time, alongside `scheduling`/`booking`/`waitlist`/`notifications`/
  `discovery`.
- This applies uniformly to both Fixed-Time and Queue-mode bookings — nothing about a
  consultation note is scheduling-mode-specific.
