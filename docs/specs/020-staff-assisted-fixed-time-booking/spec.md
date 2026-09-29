# Feature Specification: Staff-Assisted Fixed-Time Booking

**Feature Branch**: `020-staff-assisted-fixed-time-booking`

**Created**: 2026-09-03

**Status**: Draft

**Input**: User description: "Staff-Assisted Fixed-Time Booking — front-desk Operations staff (or a ClinicAdmin) book a patient into a specific OPEN Fixed-Time Slot (012) on the patient's behalf. If the patient doesn't yet exist as a clinic-scoped record, staff create it as part of the flow (reusing 009's existing Patient entity, walk-in/unlinked). Fee resolution and locking (015) runs before anything is written — if no fee can be resolved, the booking is blocked and nothing is created. On success, the Slot transitions OPEN to BOOKED and a Booking record (first defined by this feature) is created with the locked fee and a manual PENDING/PAID paymentStatus flag. Booking a Slot that's already BOOKED is rejected, race-safely. (Full source: backlog/016-staff-assisted-fixed-time-booking.md)"

## Scope Decisions Made During Drafting

No prior conversation turn resolved these — they're research-backed defaults made while writing this spec, not a `/speckit-clarify` session (which runs next and may revisit any of them).

- **This feature defines `Booking` and adds `BOOKED` to `SlotStatus`, for the first time** — no prior feature needed either. `SlotStatus` was deliberately left with only `OPEN` in 012, documented there as "extended in place exactly when a later feature needs the next value" — this is that moment.
- **A new walk-in Patient record is created via 009's existing `Patient` entity directly** (`new Patient(clinic, null, name, phone)`), not through 009's `PatientLinkingService` — that service's contract is keyed on an existing Patient Account (a self-service login identity), which a staff-entered walk-in patient with no login has none of. This feature creates an unlinked walk-in row the same shape 009's own data model already anticipated ("null for a walk-in-only record never claimed by a self-service booking").
- **Fee resolution runs, and must succeed, before any write in this flow** — before a new Patient record is created, before the Slot is touched, before any Booking row is written. This is what makes "no fee can be resolved → the booking is blocked... and no booking record is created" (and, by the same reasoning, no orphaned walk-in Patient record either) true for the whole flow, not just the Booking row specifically — a single `@Transactional` method whose first real side-effecting step is the fee resolution call achieves this by construction.
- **Race-safety on "Slot already BOOKED" is closed with a database-level uniqueness constraint on `Booking.slot`** (at most one Booking per Slot, ever), with an application-level `status == OPEN` check as the fast, common-case rejection in front of it — the same two-layer pattern (cheap check + DB constraint as the real guarantee) already established in this backlog (e.g. 014's schedule overlap check backed by no constraint since it's not a creation race, vs. 019's token issuance which is; here it genuinely is a creation race, so it gets the DB constraint).
- **Authorization is Operations or ClinicAdmin, active at the Slot's clinic — not the Doctor.** The source user story names exactly these two actors ("front-desk Operations staff (or a ClinicAdmin)"); a Doctor booking their own patients is not described anywhere in the source material for this feature (017, patient self-service, and a doctor's own equivalent if any, are separate concerns).
- **`paymentStatus` defaults to `PENDING` at booking creation and is not itself settable through this feature's booking endpoint** — the source business rule describes it as "a manual staff-flipped flag," but doesn't describe *this* feature (the act of booking) as the place that flip happens; a future feature (or a small follow-on) can add the explicit toggle action. This feature guarantees every Booking starts in a well-defined state (`PENDING`) and carries the field, satisfying "no gateway integration involved" without inventing an unrequested toggle endpoint now.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Staff Book an Existing Patient Into an Open Slot (Priority: P1)

Front-desk Operations staff (or a ClinicAdmin) select an OPEN Fixed-Time Slot and an existing clinic Patient, choose an appointment type, and book them — the Slot becomes BOOKED and a Booking record is created carrying the fee resolved and locked at that exact moment.

**Why this priority**: This is the feature's core, most common path — the entire reason it exists.

**Independent Test**: With an OPEN Slot, an existing Patient, and a resolvable fee (015) all seeded, call the booking capability; confirm a Booking is created with the correct locked fee, the Slot is now BOOKED, and `paymentStatus` starts `PENDING`.

**Acceptance Scenarios**:

1. **Given** an OPEN Fixed-Time Slot and an existing Patient at that clinic, **When** an authorized staff member books that Patient into the Slot with a valid appointment type, **Then** the Slot becomes `BOOKED`, a `Booking` is created referencing that Slot and Patient, and its `lockedFee` equals what 015's resolution would return at that moment.
2. **Given** the same successful booking, **When** the created `Booking` is inspected, **Then** its `paymentStatus` is `PENDING`.
3. **Given** a Slot that is already `BOOKED`, **When** staff attempt to book a(nother) patient into it, **Then** the attempt is rejected and no second `Booking` is ever created for that Slot — including under concurrent attempts.
4. **Given** a staff member who is neither an active Operations nor ClinicAdmin at the Slot's clinic, **When** they attempt to book, **Then** the attempt is rejected as forbidden.

---

### User Story 2 - Staff Create a Walk-In Patient as Part of Booking (Priority: P2)

A walk-in patient with no existing clinic record and no online account calls in or shows up; staff enter their name (and, optionally, phone number) directly in the booking flow, and a new clinic-scoped Patient record is created as part of the same booking action.

**Why this priority**: A clear, named extension of User Story 1's flow for the "not already a patient" case — additive, not foundational, since it reuses the exact same booking mechanics once the Patient record exists.

**Independent Test**: With an OPEN Slot and no matching Patient record, call the booking capability supplying new-patient details instead of an existing patient id; confirm a new Patient record is created and the booking proceeds identically to User Story 1 from that point on.

**Acceptance Scenarios**:

1. **Given** an OPEN Slot and a walk-in patient with no existing clinic Patient record, **When** staff supply the patient's name and book them, **Then** a new clinic-scoped Patient record is created (unlinked to any Patient Account) and the booking proceeds.
2. **Given** the same flow, **When** a phone number is also supplied and it matches the Indian numbering plan, **Then** it is saved on the new Patient record.
3. **Given** the same flow, **When** a phone number is supplied that does *not* match the Indian numbering plan, **Then** the booking attempt is rejected with a validation error, and neither a Patient nor a Booking record is created.
4. **Given** the same flow, **When** no phone number is supplied at all, **Then** the booking still proceeds — phone is optional for a walk-in.

---

### Edge Cases

- What happens when no fee can be resolved for the selected doctor/appointment type? → The booking is rejected before anything is written — no Patient record (even a new walk-in one otherwise about to be created), no Slot status change, no Booking row (spec Scope Decisions).
- What happens when the named Slot doesn't exist, or belongs to a different clinic than the one in the request? → Rejected as not found.
- What happens when the named existing Patient doesn't exist, or belongs to a different clinic? → Rejected as not found.
- What happens when the named appointment type doesn't belong to the Slot's own doctor? → Rejected, per 015's own existing "wrong doctor" rule (`AppointmentTypeNotFoundException`), reused unchanged.
- What happens when the Slot exists but is a buffer Slot? → Bookable the same as any other `OPEN` Slot — this feature's own business rules and acceptance criteria make no distinction between a buffer Slot and a regular one; a buffer Slot's special treatment (e.g. for walk-in priority) belongs to a separate, later feature (020-walk-in-priority-insertion), not this one.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow an authenticated staff member who is either an active Operations or ClinicAdmin at a Slot's clinic to book a Patient into that Slot, provided it is currently `OPEN`.
- **FR-002**: System MUST reject a booking attempt from any staff member who is neither an active Operations nor ClinicAdmin at the Slot's clinic.
- **FR-003**: System MUST allow the caller to identify the Patient either by an existing clinic-scoped Patient id, or by supplying a new patient's name (required) and phone number (optional, validated against the Indian numbering plan when present) to create one as part of the same booking.
- **FR-004**: System MUST resolve and lock the fee (015) for the Slot's doctor and the given appointment type before any other write in the booking flow, and MUST reject the entire booking attempt — creating nothing — if no fee can be resolved.
- **FR-005**: On a successful booking, system MUST transition the Slot's status from `OPEN` to `BOOKED` and create exactly one `Booking` record referencing that Slot, the Patient, and the resolved fee, with `paymentStatus` starting `PENDING`.
- **FR-006**: System MUST reject a booking attempt against a Slot that is not currently `OPEN`, and MUST NOT create a second `Booking` for a Slot that already has one — including under concurrent attempts.
- **FR-007**: System MUST reject a booking attempt naming a Slot, an existing Patient, or an Appointment Type that doesn't exist, or that belongs to a different clinic/doctor than implied by the request.
- **FR-008**: System MUST reject a booking attempt whose supplied new-patient phone number does not match the Indian numbering plan, before creating any Patient or Booking record.

### Key Entities

- **Booking** (new entity, first defined by this feature): one confirmed appointment. Fields: the Slot it's for, the Patient, the Appointment Type, the locked fee amount, a payment status (`PENDING`/`PAID`, starts `PENDING`), the staff Account that created it, a created timestamp.
- **Slot** (from 012, extended here): gains `BOOKED` as a second `SlotStatus` value.
- **Patient** (from 009, read/write here): an existing clinic Patient is read; a new walk-in Patient may be created directly, unlinked to any Patient Account.
- **AppointmentType**, **DoctorDefaultFee** (from 015, read-only here): consumed via `FeeResolutionService.resolve`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of successful bookings result in exactly one `Booking` row, the Slot transitioned to `BOOKED`, and a `lockedFee` matching what fee resolution would return at that moment.
- **SC-002**: 100% of booking attempts where no fee can be resolved create zero rows of any kind (no Patient, no Booking, no Slot status change).
- **SC-003**: 100% of booking attempts against an already-`BOOKED` Slot are rejected, including under concurrent attempts — never more than one `Booking` per Slot, verified directly at the data layer.
- **SC-004**: 100% of booking attempts by a caller outside the two authorized staff roles are rejected as forbidden.
- **SC-005**: 100% of new-walk-in-patient booking attempts with an invalid phone number are rejected before any Patient or Booking row is created.

## Assumptions

- **`paymentStatus` toggling (PENDING → PAID) is out of this feature's own scope** — every Booking this feature creates starts `PENDING`; a separate, small future action (not described in enough detail by the source material to build here) is expected to expose the actual toggle.
- **This feature is Fixed-Time only** — a Queue/Token booking's equivalent flow (018-queue-token-booking, and its own Slot-issuance mechanism, 013) is a separate, not-yet-built feature; this feature never calls `QueueSlotService` and never books against a Queue-mode Session's Slot.
- **This feature ships a minimal frontend form, not a full booking-screen design.** Its actor (Operations/ClinicAdmin staff) is real and already has a login flow today (`StaffLoginForm`), matching 013's "a real, already-authenticated actor gets a real form" precedent — but a genuine booking screen (patient search-as-you-type, a visual slot picker, live fee preview) is a substantially larger UX surface than anything specified in the source material for *this* feature. The shipped form takes the same inputs the backend contract does directly (a Slot id, an existing Patient id *or* new-patient fields, an Appointment Type id) rather than guessing at a richer picker UI a future design pass would likely replace anyway.
