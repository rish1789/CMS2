# Data Model: De-Verification Cascade (Auto-Cancel Future Bookings)

No new entity, no new migration — every entity involved already exists.

## `DoctorLicenseRevokedEvent` (new)

Plain POJO event (not extending `ApplicationEvent`), placed in `com.cms.identity.admin` alongside
`ClinicDeVerifiedEvent`, whose shape it mirrors exactly:

```java
public record DoctorLicenseRevokedEvent(UUID doctorProfileId, Instant occurredAt) {
    public static DoctorLicenseRevokedEvent of(UUID doctorProfileId) {
        return new DoctorLicenseRevokedEvent(doctorProfileId, Instant.now());
    }
}
```

Published exactly once per genuine `licenseVerified: true -> false` transition performed by the
new `DoctorVerificationService.revoke(...)` action — never by 006's automatic edit-triggered
reset (FR-009).

## `DoctorProfile` (existing, 004/006) — no schema change

`licenseVerified` already exists and is already mutable (`setLicenseVerified`, used by both
`verify()` and `edit()`'s reset path, 007/008-backlog-numbering). The new `revoke()` action reuses
the same setter — no new field, no new column.

## `Booking`/`Slot`/`Session` (existing, 016/017/018/025) — read and transitioned, not modified in shape

This feature's cascade reads `Booking.status`, `Slot.status`, `Session.mode`, and (for the
clinic-scoped query) `Session.clinic`/(for the doctor-scoped query) `Session.doctorProfile` —
every field already exists. The only write is the same `status`/`Slot.status` transition every
other cancellation path already performs.

## `BookingRepository` additions

- `findActiveFutureBookingsByClinic(UUID clinicId)` — every `Booking` with `status = ACTIVE` and
  `slot.status = BOOKED` whose `Session.clinic.id` matches (research.md R5 — no date/time filter).
- `findActiveFutureBookingsByDoctor(UUID doctorProfileId)` — the same, scoped by
  `Session.doctorProfile.id` instead, spanning every clinic that doctor is staffed at.
