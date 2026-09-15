# Data Model: Patient Self-Service Fixed-Time Booking

No new entities and no new Flyway migration. This feature is pure new behavior (a query, two
services, two endpoints, one auth filter) over entities already defined by prior converged
features.

## Reused Entities (unchanged)

| Entity | Defined by | Fields relevant to this feature |
|---|---|---|
| `PatientAccount` | 039 (`com.cms.patient.account`) | `id`, `email`, `mobile` — `id` is the authenticated principal this feature's JWT filter populates. |
| `Patient` | 019 (`com.cms.patient.record`) | `id`, `clinic`, `patientAccount`, `name`, `phone` — resolved/created via `PatientLinkingService.findOrCreatePatient`. |
| `Slot` | 012/016 (`com.cms.scheduling`) | `id`, `session`, `startTime`, `endTime`, `status` (`OPEN`/`BOOKED`) — `setStatus` flips to `BOOKED` on success. |
| `Session` | 015 (`com.cms.scheduling`) | `clinic`, `doctorProfile`, `sessionDate`, `mode` (`FIXED_TIME`/`QUEUE`) — only `FIXED_TIME` sessions' Slots are ever listed or booked here. |
| `AppointmentType` | 015 (`com.cms.booking`) | `id`, `doctorProfile`, `name`, `feeOverride` — listed by name only (see research.md); resolved fully by `FeeResolutionService` at booking time. |
| `Booking` | 016 (`com.cms.booking`) | Created exactly as 016 creates it: `slot`, `patient`, `appointmentType`, `lockedFee`, `paymentStatus=PENDING`, `bookedByAccountId`. For a patient-initiated booking, `bookedByAccountId` stores the `PatientAccount.id` (the same field 016 uses for the staff caller's `Account.id` — both are "who initiated this booking," just from different identity systems; no schema change needed since it's an opaque `UUID` column). |
| `DoctorProfile` | 004/007 (`com.cms.identity.doctor`) | `id`, `account` — `account.getName()` supplies the doctor display name in the list response. |

## New Query

`SlotRepository` gains one method (no new table, no migration):

```java
@Query("SELECT s FROM Slot s "
     + "WHERE s.session.clinic.id = :clinicId "
     + "AND s.session.mode = com.cms.scheduling.ScheduleMode.FIXED_TIME "
     + "AND s.status = com.cms.scheduling.SlotStatus.OPEN "
     + "AND (:doctorProfileId IS NULL OR s.session.doctorProfile.id = :doctorProfileId) "
     + "ORDER BY s.session.sessionDate ASC, s.startTime ASC")
List<Slot> findOpenFixedTimeSlots(
        @Param("clinicId") UUID clinicId, @Param("doctorProfileId") UUID doctorProfileId);
```

Excludes Queue-mode Sessions' Slots (their `startTime`/`endTime` are null and they are not
Fixed-Time Slots per this feature's scope — see 018-queue-token-booking, a separate feature).

## New DTOs

- **`OpenSlotResponse`**: `slotId`, `doctorProfileId`, `doctorName`, `sessionDate`,
  `startTime`, `endTime`, `appointmentTypes: List<AppointmentTypeResponse>` (reuses 015's
  existing `AppointmentTypeResponse(id, doctorProfileId, name, feeOverride)` record as-is —
  see contracts/patient-booking.md for whether `feeOverride` is nulled out in this context).
- **`PatientBookSlotRequest`**: `patientName` (required — used only if a new `Patient` record
  must be created), `appointmentTypeId` (required).

## State Transitions

Identical to 016: `Slot.status` `OPEN` → `BOOKED`, one-way, exactly once per Slot, enforced by
the existing `uq_booking_slot` unique index (no new constraint needed).
