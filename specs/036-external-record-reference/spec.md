# Feature Specification: External Record Reference

**Feature Branch**: `036-external-record-reference`

**Created**: 2026-09-04

**Status**: Draft

**Input**: User description: "032 — External Record Reference: As a Doctor, I want to record a typed summary reference to an external clinical record (e.g. a lab result, an imaging report, a prior diagnosis from another provider) for a patient I personally treated in a specific booking, so that relevant outside context is captured without requiring any file handling. Typed summaries only — never file uploads. Strictly immutable once created. Only the treating doctor for that specific booking may create it, no clinic-ownership override. A booking may have zero or more, each independently created and immutable."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Treating Doctor Records a Typed External Record Reference (Priority: P1) 🎯 MVP

The doctor who treated a patient for a booking notes that a relevant external clinical record
exists (a lab result, an imaging report, a prior diagnosis from another provider) by recording a
typed summary of it — never an uploaded file — which can never be altered by anyone afterward,
including the doctor who wrote it.

**Why this priority**: The feature's entire named purpose, and the third and final feature
completing the constitution's own named "clinical documentation" module trio (030, 031, 032).

**Independent Test**: As the doctor assigned to a booking's slot, create an External Record
Reference with its typed fields for it, then confirm it's retrievable, immutable, and that a
second, independent reference can also be created for the same booking.

**Acceptance Scenarios**:

1. **Given** a booking whose slot is assigned to a treating doctor, **When** that doctor creates
   an External Record Reference with typed fields (record type, source/provider name, date,
   summary text) for it, **Then** it is saved and immediately, permanently immutable.
2. **Given** an External Record Reference creation form, **When** a doctor looks for a way to
   attach a file (image, PDF, scan), **Then** no such field or capability exists anywhere — only
   structured typed fields are accepted.
3. **Given** an External Record Reference already exists for a booking, **When** anyone —
   including the original author — attempts to edit or delete it, **Then** the system rejects it;
   no update/delete action exists for it, anywhere.
4. **Given** a booking already has one External Record Reference, **When** its treating doctor
   creates a second, independent one for the same booking, **Then** it succeeds — a booking may
   carry zero or more, each independently created (mirrors 031-prescription-and-items-creation's
   own identical cardinality, not 030-consultation-note-creation's cap of one).
5. **Given** a doctor who is NOT the treating doctor for a booking — including a different doctor
   at the same clinic, a ClinicAdmin, or Operations staff — **When** they attempt to create an
   External Record Reference for it, **Then** the system rejects the action; no override exists
   under any circumstance.
6. **Given** an External Record Reference exists for a booking, **When** its treating doctor
   retrieves it, **Then** they see its full typed content — a permanent clinical record has to
   actually be readable to serve its stated purpose.

---

### Edge Cases

- What happens if someone tries to create a reference for a booking that doesn't exist? Rejected
  — not found.
- What happens if the booking's slot has been cancelled or the patient never showed up? No
  precondition on the booking's or slot's status is stated anywhere in the source material —
  mirrors 030's and 031's own identical resolution.
- Who besides the treating doctor can ever read a reference? Out of scope for this feature to
  define broader clinic-wide or patient-facing read access — mirrors 030's and 031's own
  identical scoping.
- What happens if the record date is left blank or is in the future? No constraint on the date
  field's value is stated in the source material — accepted as submitted; this is a
  doctor-recorded summary field, not a system-validated fact.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow the doctor assigned to a booking's slot (its "treating
  doctor") to create an External Record Reference for that booking, recording a record type,
  source/provider name, date, and summary text.
- **FR-002**: An External Record Reference MUST be permanently immutable once created — no update
  or delete action MUST exist for it anywhere in the system.
- **FR-003**: The system MUST allow a booking to carry zero or more independently-created
  External Record References — no uniqueness constraint caps this.
- **FR-004**: The system MUST reject an External Record Reference creation attempt by anyone
  other than the specific booking's treating doctor — including a different doctor, a
  ClinicAdmin, or Operations staff at the same clinic — with no override path of any kind.
- **FR-005**: Authorization MUST be determined solely by tracing the booking to its slot's
  assigned doctor and comparing that to the acting doctor's own identity — mirrors 030's and
  031's identical rule; no separate clinic-staffing-status check is required.
- **FR-006**: An External Record Reference MUST NOT have any file/document attachment field
  anywhere — only structured, typed fields are ever accepted.
- **FR-007**: The system MUST allow the treating doctor to retrieve an External Record Reference
  they authored.
- **FR-008**: The system MUST NOT impose any precondition on the booking's or its slot's status
  (e.g. completed, cancelled) before an External Record Reference may be created.

### Key Entities

- **ExternalRecordReference** *(new)*: A permanent, immutable typed-summary record belonging to
  exactly one Booking; a Booking may have zero or more. Fields: the Booking it belongs to, the
  authoring (treating) doctor, record type, source/provider name, record date, summary text, and
  its own creation timestamp. Never a file/document attachment.
- **Booking** *(existing, 016/017/018)*: Read-only for this feature — supplies the treating
  doctor (via its Slot's Session) that authorization is traced against; nothing about the Booking
  itself is written by this feature.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of External Record Reference creation attempts by a booking's actual treating
  doctor succeed and are permanently retrievable afterward.
- **SC-002**: 100% of creation attempts by anyone other than the treating doctor are rejected,
  with zero exceptions or overrides.
- **SC-003**: A single booking can carry an unlimited number of independently-created External
  Record References with no rejection due to count.
- **SC-004**: 0% of External Record References are ever altered or removed after creation — no
  code path exists that could do so.
- **SC-005**: 0% of External Record References ever carry a file/document attachment — no such
  field exists in the system.

## Assumptions

- Every External Record Reference requires `recordType`, `sourceProvider`, `recordDate`, and
  `summary` — the source material's own "record type, source/provider name, date, summary text"
  lists these as the concrete fields, all required (unlike 031's optional `instructions`, nothing
  in this feature's own source text suggests any field here is optional).
- This feature reuses `com.cms.clinical.TreatingDoctorAuthorizationService` (extracted during
  031-prescription-and-items-creation, already shared by 030 and 031) as its own, third caller —
  not a new implementation of the identical authorization trace.
- Cardinality matches 031's shape (zero or more per booking, no uniqueness constraint), not 030's
  (capped at one) — stated directly in the source material.
- Only the treating doctor's own read access is in scope — mirrors 030's and 031's identical
  scoping; a future feature can extend read access if that's ever a stated requirement.
- `ExternalRecordReference` and this feature's service/controller live in the same
  `com.cms.clinical` module 030 already materialized — not a new module boundary.
- This applies uniformly to both Fixed-Time and Queue-mode bookings — nothing about a reference
  is scheduling-mode-specific, mirroring 030 and 031.
