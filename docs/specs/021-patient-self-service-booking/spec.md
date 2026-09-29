# Feature Specification: Patient Self-Service Fixed-Time Booking

**Feature Branch**: `021-patient-self-service-booking`

**Created**: 2026-09-03

**Status**: Draft

**Input**: User description: "017 — Patient Self-Service Fixed-Time Booking: As a logged-in Patient Account holder, I want to browse open Fixed-Time Slots at a clinic and book one myself, so that I don't need to call or visit in person to get an appointment. Mirrors 016-staff-assisted-fixed-time-booking but scoped to the logged-in patient's own booking only, with fee resolution/locking per 015 and Patient-record auto-creation/phone-linking per 019."

## Clarifications

### Session 2026-09-03

- Q: Should this feature include a new endpoint/UI for patients to browse a clinic's open Fixed-Time Slots, or is direct booking against an already-known Slot ID sufficient for v1? → A: Add a new patient-facing "list open Slots for a clinic/doctor" endpoint + UI as part of this feature's scope.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Book an Open Slot as an Already-Linked Patient (Priority: P1) 🎯 MVP

A logged-in Patient Account holder who already has a clinic-scoped Patient record at a clinic (from a prior visit or a prior booking) browses that clinic's open Fixed-Time Slots and books one for themselves.

**Why this priority**: This is the core value of the feature — self-service booking without staff involvement. Without it, nothing else in this feature has any purpose.

**Independent Test**: Log in as a Patient Account holder already linked to a clinic's Patient record, request the list of that clinic's open Slots, submit a booking against one of the returned OPEN Slots, and confirm the Slot becomes BOOKED with a Booking created under the correct Patient record and a resolved, locked fee.

**Acceptance Scenarios**:

1. **Given** a logged-in Patient Account holder, **When** they request the list of open Fixed-Time Slots for a clinic (optionally filtered by doctor), **Then** the system returns only currently-OPEN Slots at that clinic with enough detail (doctor, date/time, appointment-type options) to choose one.
2. **Given** a logged-in Patient Account holder already linked to a Patient record at a clinic, **When** they submit a booking for an OPEN Slot at that clinic with a valid appointment type, **Then** the Slot becomes BOOKED and a Booking is created under their existing Patient record, with a resolved, locked fee and payment status pending, and that Slot no longer appears in a subsequent list request.
3. **Given** the Slot they attempt to book has just been taken by someone else, **When** they submit the booking, **Then** the booking is rejected with a "no longer available" error and no double-booking occurs.
4. **Given** no fee can be resolved for the selected appointment type/doctor, **When** the patient attempts to book, **Then** the booking is blocked with a clear reason shown to the patient, and no Booking is created.
5. **Given** an unauthenticated visitor (no valid Patient Account session), **When** they attempt to list Slots or submit a booking, **Then** the request is rejected and no Booking is created.
6. **Given** a logged-in Patient Account holder, **When** they attempt to book an OPEN Slot at a clinic where they have no existing Patient record, **Then** the booking still succeeds (covered by User Story 2's auto-creation behavior) rather than being rejected for lacking a record.

---

### User Story 2 - First-Ever Booking at a Clinic Auto-Creates the Patient Record (Priority: P2)

A logged-in Patient Account holder books a Slot at a clinic they have never visited or booked at before. No clinic-scoped Patient record yet exists for them there (or an unlinked walk-in record with a matching phone number does).

**Why this priority**: Without this, only patients who already happen to have a Patient record at a clinic (created some other way) could use self-service booking at all — which would make the feature nearly useless for genuinely new patients. It builds directly on User Story 1's booking mechanics.

**Independent Test**: Log in as a Patient Account holder with no Patient record at a given clinic, submit a booking there, and confirm both a new (or phone-matched, auto-linked) Patient record and the Booking are created correctly.

**Acceptance Scenarios**:

1. **Given** a logged-in Patient Account holder with no existing Patient record at a clinic and no matching walk-in record there, **When** they submit a booking for an OPEN Slot at that clinic, **Then** a new clinic-scoped Patient record is created and linked to their account, and the booking proceeds as in User Story 1.
2. **Given** a logged-in Patient Account holder whose account phone number matches an existing unlinked walk-in Patient record at that clinic, **When** they submit their first booking there, **Then** that existing walk-in record is linked to their account (not duplicated) and the booking is created under it.

---

### Edge Cases

- What happens when the patient submits a booking for a Slot at a clinic that does not exist, or a Slot ID that does not exist (or belongs to a different clinic)? The booking is rejected with a not-found error and nothing is created.
- What happens when the selected appointment type does not exist or does not belong to the Slot's doctor? The booking is rejected with a clear error and nothing is created.
- What happens when a Patient Account's session token has expired or is otherwise invalid? The request is rejected as unauthenticated.
- What happens when two concurrent requests race to book the exact same Slot (whether from the same patient double-submitting or two different patients)? Exactly one booking succeeds; the other is rejected with a "no longer available" error.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow only an authenticated Patient Account holder to submit a self-service booking; unauthenticated requests MUST be rejected.
- **FR-002**: A patient MUST be able to book only an OPEN Slot; attempting to book a Slot that is not OPEN MUST be rejected with a "no longer available" error, without creating a Booking.
- **FR-003**: The system MUST resolve and lock a fee for the selected doctor/appointment-type pairing before creating any Booking; if no fee can be resolved, the booking MUST be blocked with a clear, patient-facing reason and nothing MUST be created.
- **FR-004**: On a patient's first-ever booking at a given clinic, the system MUST automatically create (or auto-link an existing unlinked walk-in record by phone match) a clinic-scoped Patient record for them, with no staff action required.
- **FR-005**: On a patient's subsequent booking at a clinic where they already have a linked Patient record, the system MUST reuse that existing record rather than creating a duplicate.
- **FR-006**: On successful booking, the system MUST transition the Slot from OPEN to BOOKED and create a Booking associated with the patient's clinic-scoped Patient record, the resolved locked fee, and a pending payment status.
- **FR-007**: The system MUST prevent a double-booking of the same Slot even when two booking attempts race concurrently — exactly one MUST succeed.
- **FR-008**: A patient MUST only be able to book for themselves through this flow; the system MUST NOT accept a booking submitted on behalf of a different person (no proxy-booking).
- **FR-009**: The system MUST reject a booking attempt for a Slot ID that does not exist, or that does not belong to the specified clinic, with a not-found error.
- **FR-010**: The system MUST reject a booking attempt referencing an appointment type that does not exist, or does not belong to the Slot's doctor, with a clear error.
- **FR-011**: The system MUST allow an authenticated Patient Account holder to retrieve the list of currently-OPEN Fixed-Time Slots at a given clinic, optionally filtered by doctor, showing enough detail to choose one to book; unauthenticated requests MUST be rejected.
- **FR-012**: A Slot that transitions away from OPEN (e.g. becomes BOOKED) MUST NOT appear in a subsequent list request.

### Key Entities

- **Patient Account** *(existing, from 039)*: The patient's global self-service login identity; the authenticated actor for this feature.
- **Patient** *(existing, from 019)*: The clinic-scoped clinical/visit record a booking is created under; auto-created or auto-linked to the Patient Account on first booking at a clinic.
- **Slot** *(existing, from 012)*: A bookable Fixed-Time appointment slot; transitions from OPEN to BOOKED when this feature's booking succeeds.
- **Booking** *(existing, from 016)*: The record created by a successful booking, associating a Slot, a Patient, an appointment type, and a locked fee.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A logged-in patient can complete a self-service booking of an OPEN Slot, from submission to confirmation, in a single request-response round trip with no staff involvement.
- **SC-002**: 100% of bookings attempted against a Slot that has already been taken (including under concurrent-race conditions) are rejected without creating a duplicate Booking or leaving the Slot in an inconsistent state.
- **SC-003**: 100% of bookings for which no fee can be resolved are blocked before any Patient or Booking record is created, with a reason shown to the patient.
- **SC-004**: A new patient's very first booking at a clinic succeeds without requiring any separate "create my patient record" step — the record is created or linked automatically as part of the booking itself.
- **SC-005**: A patient can go from "which slots are open at this clinic" to a confirmed booking using only information returned by this feature's own list view — no need to already know a specific Slot ID in advance.

## Assumptions

- Reuses 015's fee-resolution mechanics and 019's Patient auto-creation/phone-linking mechanics exactly as those features define them; this feature does not alter either.
- Reuses 016's Slot/Booking data model (Slot, SlotStatus OPEN/BOOKED, Booking, locked fee, pending payment status) as-is; no new Slot or Booking state is introduced.
- A patient's own session (via their existing Patient Account login from 039) is the sole authentication mechanism for this flow — no separate patient-booking-specific credential.
- Online payment collection at time of booking is out of scope (per 015); payment status starts pending with no toggle built.
- Proxy-booking on behalf of another person is out of scope for v1.
- Reschedule is out of scope — cancel-then-rebook only (a future feature's concern).
- This feature ships a minimal patient-facing frontend: a list of a clinic's open Slots plus a booking form, consistent in spirit with the minimalism of 016's staff-facing booking form but extended (per Clarifications) to include the list view.
- The list-Slots capability is clinic-scoped read access to Slot availability, not a new public discovery/search surface — it stays behind Patient Account authentication like the rest of this feature, distinct from 010-public-discovery-search's unauthenticated doctor/clinic search.
