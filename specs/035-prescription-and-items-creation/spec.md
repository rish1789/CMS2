# Feature Specification: Prescription + Items Creation

**Feature Branch**: `035-prescription-and-items-creation`

**Created**: 2026-09-04

**Status**: Draft

**Input**: User description: "031 — Prescription + Items Creation: As a Doctor, I want to record a prescription with its line-item medications for a patient I personally treated in a specific booking, so that there is a lightweight, permanent clinical record of what was prescribed during that visit. Strictly immutable once created — no edit function anywhere. Only the treating doctor for that specific booking may create a Prescription for it, traced via booking->slot->assigned doctor, no clinic-ownership override. A booking may have zero or more Prescriptions (unlike Consultation Notes, which cap at one). No file upload — structured, typed fields only."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Treating Doctor Records a Prescription with Line-Item Medications (Priority: P1) 🎯 MVP

The doctor who treated a patient for a booking records a prescription — one or more individual
medication line items — as a permanent record of what was prescribed, which can never be altered
by anyone afterward, including the doctor who wrote it.

**Why this priority**: The feature's entire named purpose, and the second feature to build on the
constitution's own named "clinical documentation" module (030 was the first).

**Independent Test**: As the doctor assigned to a booking's slot, create a prescription with one
or more medication items for it, then confirm it's retrievable, immutable, and that a second,
independent prescription can also be created for the same booking.

**Acceptance Scenarios**:

1. **Given** a booking whose slot is assigned to a treating doctor, **When** that doctor creates a
   Prescription with one or more Items for it, **Then** the Prescription and its Items are saved
   and immediately, permanently immutable.
2. **Given** a Prescription already exists for a booking, **When** anyone — including the
   original prescribing doctor — attempts to edit or delete it or any of its Items, **Then** the
   system rejects it; no update/delete action exists for either, anywhere.
3. **Given** a booking already has one Prescription, **When** its treating doctor creates a second,
   independent Prescription for the same booking, **Then** it succeeds — unlike Consultation Notes
   (030), a booking may carry zero or more Prescriptions.
4. **Given** a doctor who is NOT the treating doctor for a booking — including a different doctor
   at the same clinic, a ClinicAdmin, or Operations staff — **When** they attempt to create a
   Prescription for it, **Then** the system rejects the action; no override exists under any
   circumstance.
5. **Given** a doctor wants to correct a prescribing error, **When** they act on it, **Then** the
   only available path is creating a brand-new Prescription — never editing the original, which
   remains permanently unchanged.
6. **Given** a Prescription Item is being created, **When** the doctor submits it, **Then** only
   structured, typed fields (medication name, dosage, frequency, duration, instructions) are
   accepted — no file/document attachment field exists anywhere on it.
7. **Given** a Prescription exists for a booking, **When** its treating doctor retrieves it,
   **Then** they see its full content including every Item — a permanent clinical record has to
   actually be readable to serve its stated purpose.

---

### Edge Cases

- What happens if a Prescription is submitted with zero Items? Rejected — a Prescription with no
  medications isn't a meaningful record; at least one Item is required.
- What happens if someone tries to create a Prescription for a booking that doesn't exist?
  Rejected — not found.
- What happens if the booking's slot has been cancelled or the patient never showed up? No
  precondition on the booking's or slot's status is stated anywhere in the source material —
  mirrors 030's own identical resolution; a Prescription can be created as long as the booking
  exists and the acting doctor is its treating doctor.
- Who besides the treating doctor can ever read a Prescription? Out of scope for this feature to
  define broader clinic-wide or patient-facing read access — mirrors 030's own identical scoping;
  only the treating doctor's own read path is in scope here.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow the doctor assigned to a booking's slot (its "treating
  doctor") to create a Prescription for that booking, consisting of one or more Items.
- **FR-002**: A Prescription and its Items MUST be permanently immutable once created — no update
  or delete action MUST exist for either anywhere in the system.
- **FR-003**: The system MUST allow a booking to carry zero or more independently-created
  Prescriptions — no uniqueness constraint caps this, unlike Consultation Notes (030).
- **FR-004**: The system MUST reject a Prescription creation attempt by anyone other than the
  specific booking's treating doctor — including a different doctor, a ClinicAdmin, or Operations
  staff at the same clinic — with no override path of any kind.
- **FR-005**: Authorization MUST be determined solely by tracing the booking to its slot's
  assigned doctor and comparing that to the acting doctor's own identity — mirrors 030's identical
  rule; no separate clinic-staffing-status check is required.
- **FR-006**: The system MUST reject a Prescription creation attempt with zero Items.
- **FR-007**: Each Prescription Item MUST record only structured, typed fields (medication name,
  dosage, frequency, duration, and optional instructions) — no file/document attachment field MUST
  exist anywhere on it.
- **FR-008**: The system MUST allow the treating doctor to retrieve a Prescription they authored,
  including all of its Items.
- **FR-009**: The system MUST NOT impose any precondition on the booking's or its slot's status
  (e.g. completed, cancelled) before a Prescription may be created.

### Key Entities

- **Prescription** *(new)*: A permanent, immutable clinical record belonging to exactly one
  Booking; a Booking may have zero or more. Fields: the Booking it belongs to, the authoring
  (treating) doctor, its creation timestamp, and one or more Items.
- **PrescriptionItem** *(new)*: One medication line entry belonging to exactly one Prescription.
  Fields: medication name, dosage, frequency, duration, optional instructions — structured, typed
  data only, never a file.
- **Booking** *(existing, 016/017/018)*: Read-only for this feature — supplies the treating doctor
  (via its Slot's Session) that authorization is traced against; nothing about the Booking itself
  is written by this feature.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of Prescription creation attempts by a booking's actual treating doctor, with
  at least one Item, succeed and are permanently retrievable afterward, including every Item.
- **SC-002**: 100% of Prescription creation attempts by anyone other than the treating doctor are
  rejected, with zero exceptions or overrides.
- **SC-003**: A single booking can carry an unlimited number of independently-created
  Prescriptions with no rejection due to count.
- **SC-004**: 0% of Prescriptions or Items are ever altered or removed after creation — no code
  path exists that could do so.
- **SC-005**: 100% of Prescription creation attempts with zero Items are rejected.

## Assumptions

- Every Prescription Item requires `medicationName`, `dosage`, `frequency`, and `duration`;
  `instructions` alone is optional — the source material's own "medication name, dosage,
  frequency, duration, instructions, etc." lists these as the concrete fields, with "instructions"
  read as elaboration rather than a strictly required field for every entry.
- Prescriptions and their Items share this feature's own authorization trace logic with 030's
  already-converged Consultation Notes — implemented as one shared helper both features' services
  call, now that a second real caller exists for the identical logic (Constitution II: extract on
  genuine reuse, not speculatively).
- Only the treating doctor's own read access is in scope — mirrors 030's identical scoping; a
  future feature can extend read access if that's ever a stated requirement.
- `Prescription`/`PrescriptionItem` and this feature's service/controller live in the same
  `com.cms.clinical` module 030 already materialized — not a new module boundary.
- This applies uniformly to both Fixed-Time and Queue-mode bookings — nothing about a Prescription
  is scheduling-mode-specific, mirroring 030.
