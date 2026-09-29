# Data Model: Staff-Assisted Fixed-Time Booking

## `SlotStatus` (extended — 012)

Gains `BOOKED`. `Slot` gains a `setStatus(SlotStatus)` mutator (012 only ever constructed a Slot as `OPEN`, with no way to change it afterward).

## `PaymentStatus` (new enum)

`PENDING`, `PAID` — this feature only ever sets `PENDING` (spec Assumptions); `PAID` exists as the documented target of a future toggle this feature doesn't itself build.

## `Booking` (new entity)

| Field | Type | Notes |
|---|---|---|
| `id` | UUID, generated | |
| `slot` | `Slot`, `@OneToOne`, required, unique | At most one Booking per Slot, ever (FR-006) |
| `patient` | `Patient`, `@ManyToOne`, required | |
| `appointmentType` | `AppointmentType`, `@ManyToOne`, required | |
| `lockedFee` | `BigDecimal(10,2)`, required | Snapshotted from `FeeResolutionService.resolve` at booking time |
| `paymentStatus` | `PaymentStatus`, required | Always `PENDING` at creation |
| `bookedByAccountId` | UUID, required | The staff Account that made this booking |
| `createdAt` | `Instant`, defaulted `now()` | |

## Service flow

### `StaffBookingService.bookSlot(UUID callerAccountId, UUID clinicId, UUID slotId, BookSlotRequest request) -> Booking` (`@Transactional`)

1. Load `Slot` by `slotId`; if not found, or its `session.clinic.id != clinicId`, throw `SlotNotFoundException` (FR-007).
2. `requireAuthorized(callerAccountId, clinicId)` — active Operations or ClinicAdmin at `clinicId` (FR-001/FR-002).
3. If `slot.getStatus() != OPEN`, throw `SlotAlreadyBookedException` (FR-006, fast path).
4. Resolve the Patient's clinic-scoped id needed for fee resolution (the Slot's own doctor): `doctorProfileId = slot.getSession().getDoctorProfile().getId()`.
5. **First real write-gate**: `lockedFee = feeResolutionService.resolve(doctorProfileId, request.appointmentTypeId())` — throws `AppointmentTypeNotFoundException`/`NoFeeConfiguredException` (015's existing exceptions, reused unchanged) if unresolvable; nothing has been written yet (FR-004).
6. Resolve or create the `Patient`:
   - If `request.patientId()` is set: load `Patient` by id; if not found, or its `clinic.id != clinicId`, throw `PatientNotFoundException`.
   - Else: validate `request.patientName()` is present and `request.patientPhone()` (if present) passes `IndianMobileNumberValidator`, or throw `InvalidMobileNumberException` (FR-008) — then `new Patient(clinic, null, name, phone)`, saved.
7. Build and save `Booking(slot, patient, appointmentType, lockedFee, PENDING, callerAccountId)`. A unique-constraint violation on `slot_id` here (the rare concurrent-race case) is caught and re-thrown as `SlotAlreadyBookedException` (research.md).
8. `slot.setStatus(BOOKED)`; save. Return the `Booking`.

## Request/Response contract

See `contracts/staff-booking.md` — `POST /api/v1/clinics/{clinicId}/slots/{slotId}/book`, under the existing `/api/v1/clinics/**` chain.
