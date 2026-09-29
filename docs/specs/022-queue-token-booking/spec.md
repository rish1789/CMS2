# Feature Specification: Queue/Token Booking

**Feature Branch**: `022-queue-token-booking`

**Created**: 2026-09-03

**Status**: Draft

**Input**: User description: "018 — Queue/Token Booking: As a patient (self-service) or staff member (assisted), I want to book into a Queue/Token Session and receive a token number reflecting my place in the queue. Booking itself creates the Slot (via 013's QueueSlotService) and assigns the next never-reused token number; fee resolution/locking per 015, Patient-record auto-creation/phone-linking per 019, mirroring 016/017's staff-assisted and patient-self-service Fixed-Time booking mechanics but for Queue mode."

## Clarifications

### Session 2026-09-03

- Q: Should this feature include a way for a patient to browse which Queue/Token Sessions are currently active at a clinic, or is booking against an already-known Session ID sufficient for v1? → A: No session-browsing capability — book by already-known Session ID only (mirrors 016's original staff-booking precedent). A queue-session list would show little of value until 024-queue-position-tracking (a separate, not-yet-built feature) exists to supply wait/position data.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Staff Books a Walk-In or Existing Patient into a Queue Session (Priority: P1) 🎯 MVP

An Operations staff member or ClinicAdmin books a patient (an existing clinic-scoped Patient record, or a new walk-in) into an active Queue/Token Session, receiving a token number for that booking.

**Why this priority**: Mirrors 016's own priority reasoning — queue-mode clinics still need a staff-assisted path as their baseline, and it's the simpler of the two actors (no separate authentication system to build).

**Independent Test**: As Operations/ClinicAdmin staff, submit a booking against an active Queue/Token Session for an existing Patient, and confirm a new Slot with the next token number is created, a Booking is created with a resolved, locked fee, and the Booking never carries a delay-in-minutes figure.

**Acceptance Scenarios**:

1. **Given** an active Queue/Token Session and an existing Patient record at that clinic, **When** Operations or ClinicAdmin staff submit a booking for that patient, **Then** a new Slot is created with the next unused token number for that Session, a Booking is created with a resolved, locked fee and pending payment status, and the response contains no delay-in-minutes figure.
2. **Given** a walk-in with no existing Patient record, **When** staff submit a queue booking with the walk-in's name (and optional phone), **Then** a new clinic-scoped Patient record is created as part of the same action and the booking proceeds as in Scenario 1.
3. **Given** no fee can be resolved for the selected appointment type/doctor, **When** staff attempt the booking, **Then** the booking is blocked with a clear reason and no Slot, Booking, or new Patient row is created.
4. **Given** a Doctor's own token, or an unrelated staff member's token, **When** they attempt this booking, **Then** the request is rejected as not authorized.
5. **Given** a Session that is not in Queue/Token mode, **When** a queue booking is attempted against it, **Then** it is rejected with a clear error.

---

### User Story 2 - Patient Self-Service Books into a Queue Session (Priority: P2)

A logged-in Patient Account holder books themselves into an active Queue/Token Session at a clinic, receiving a token number.

**Why this priority**: Mirrors 017/021's own priority relationship to 016 — the patient-facing path builds on the same underlying mechanics as US1 and depends on Patient Account authentication already being in place.

**Independent Test**: As a logged-in Patient Account holder, submit a booking against an active Queue/Token Session and confirm a Slot/Booking are created under their own (existing or newly linked) Patient record.

**Acceptance Scenarios**:

1. **Given** a logged-in Patient Account holder already linked to a Patient record at a clinic, **When** they submit a booking against an active Queue/Token Session at that clinic, **Then** a new Slot is created with the next unused token number, and a Booking is created under their existing Patient record with a resolved, locked fee.
2. **Given** this is the patient's first-ever booking at this clinic, **When** the queue booking completes, **Then** a new clinic-scoped Patient record is created and linked to their account (or an existing walk-in record is auto-linked by phone match, per 019), exactly as in the Fixed-Time self-service path.
3. **Given** an unauthenticated visitor, **When** they attempt this booking, **Then** the request is rejected and nothing is created.

---

### Edge Cases

- What happens when the target Session doesn't exist or belongs to a different clinic than specified? The booking is rejected with a not-found error and nothing is created.
- What happens when the selected appointment type doesn't exist or doesn't belong to the Session's doctor? The booking is rejected with a clear error and nothing is created.
- What happens when two booking requests race to book into the same Queue Session at nearly the same instant? Each gets its own newly-created Slot with a distinct, never-reused token number — there is no "already booked" rejection case in Queue mode, since each booking mints a fresh Slot rather than contending for a pre-existing one (unlike Fixed-Time booking).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow only an active Operations staff member or ClinicAdmin at the target clinic to submit a staff-assisted queue booking; a Doctor or unrelated staff member MUST be rejected.
- **FR-002**: The system MUST allow only an authenticated Patient Account holder to submit a self-service queue booking; unauthenticated requests MUST be rejected.
- **FR-003**: A queue booking MUST create exactly one new Slot, with the next never-reused token number for the target Session, as part of the same action.
- **FR-004**: The system MUST reject a queue booking attempt against a Session that is not in Queue/Token mode, with a clear error, and create nothing.
- **FR-005**: The system MUST resolve and lock a fee for the selected doctor/appointment-type pairing before creating any Slot or Booking; if no fee can be resolved, the booking MUST be blocked with a clear reason and nothing MUST be created.
- **FR-006**: On a staff-assisted booking for a walk-in with no existing Patient record, the system MUST create a new clinic-scoped Patient record as part of the same action.
- **FR-007**: On a patient self-service booking, the system MUST resolve or create the caller's clinic-scoped Patient record exactly per 019's auto-creation/phone-linking behavior — reusing an existing link, phone-matching an unlinked walk-in record, or creating a new record.
- **FR-008**: A completed queue Booking MUST NOT carry a delay-in-minutes figure.
- **FR-009**: The system MUST reject a booking attempt for a Session ID that does not exist, or that does not belong to the specified clinic, with a not-found error.
- **FR-010**: The system MUST reject a booking attempt referencing an appointment type that does not exist, or does not belong to the Session's doctor, with a clear error.
- **FR-011**: A patient MUST only be able to self-service-book for themselves through this flow; the system MUST NOT accept a booking submitted on behalf of a different person.

### Key Entities

- **Session** *(existing, from 015)*: The Queue/Token session a booking targets; `mode = QUEUE` is required.
- **Slot** *(existing, from 012/019)*: Created fresh by this feature via `QueueSlotService`, with a token number instead of a time window.
- **Patient** *(existing, from 019)*: The clinic-scoped record a booking is created under; auto-created or auto-linked exactly as in the Fixed-Time booking features.
- **Booking** *(existing, from 016)*: The record created by a successful queue booking, associating the new Slot, a Patient, an appointment type, and a locked fee — no delay figure is ever attached.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A staff member or patient can complete a queue booking, from submission to confirmation with an assigned token number, in a single request-response round trip.
- **SC-002**: 100% of queue bookings for which no fee can be resolved are blocked before any Slot, Patient, or Booking record is created.
- **SC-003**: Every completed queue Booking's response is verified to omit a delay-in-minutes figure.
- **SC-004**: Concurrent queue bookings against the same Session each receive a distinct, never-reused token number with no collisions, even under simultaneous submission.
- **SC-005**: A new patient's very first self-service queue booking at a clinic succeeds without requiring any separate "create my patient record" step.

## Assumptions

- Reuses 013's `QueueSlotService.issueNextSlot` for Slot/token creation, 015's fee-resolution mechanics, 019's Patient auto-creation/phone-linking, and 016's `Booking` data model, exactly as those features define them; this feature does not alter any of the four.
- No pre-existing-Slot race exists to close here (unlike Fixed-Time booking): each queue booking mints a brand-new Slot via `QueueSlotService`, so there is no "this Slot was just taken by someone else" rejection case for Queue mode.
- Online payment collection at time of booking is out of scope (per 015); payment status starts pending with no toggle built.
- Proxy-booking on behalf of another person is out of scope for v1 (self-service path), consistent with the Fixed-Time self-service feature.
- Queue-position calculation/display (024) and walk-in priority insertion (020) are separate, not-yet-built features this feature does not implement or wire into.
- This feature ships minimal staff-facing and patient-facing frontend forms, consistent with the precedent set by 016/017's Fixed-Time booking forms.
- Per Clarifications, both actors book by an already-known Session ID; this feature builds no Session-listing/browsing capability (deferred pending 024-queue-position-tracking).
