# Phase 1 Data Model: Monthly Automatic Retention Purge

No new tables or columns. This feature only reads existing entities and deletes rows from three existing tables.

## Entities touched (all pre-existing)

### Booking (`com.cms.booking.Booking`)
- Read-only for this feature.
- `createdAt` (`Instant`) — the retention-clock anchor (research.md R1).
- `patient` (`Patient`, many-to-one) — reached to check `anonymizedAt`.
- Never modified or deleted by this feature.

### Patient (`com.cms.patient.record.Patient`)
- Read-only for this feature.
- `anonymizedAt` (`Instant`, nullable) — non-null means eligible; `isAnonymized()` accessor already exists (037).
- Never modified or deleted by this feature (the anonymized shell persists indefinitely per spec FR-004).

### ConsultationNote (`com.cms.clinical.ConsultationNote`)
- Deletion target. Zero-or-one per booking (unique constraint on `booking_id`, from 034/030).
- Deleted via `consultationNoteRepository.deleteById(id)` when its booking is eligible.

### Prescription (`com.cms.clinical.Prescription`) + PrescriptionItem
- Deletion target. Zero-or-more `Prescription`s per booking (035/031), each with one-or-more `PrescriptionItem`s.
- **Schema/mapping change**: `Prescription.items`' `@OneToMany` cascade extended from `{PERSIST}` to `{PERSIST, REMOVE}` (research.md R3) so `prescriptionRepository.deleteById(id)` cascades to its items. No Flyway migration needed — this is a JPA mapping change, not a schema change (FK `prescription_item.prescription_id` already exists with the correct shape; only the cascade *behavior* on the Java side changes).
- Deleted via `prescriptionRepository.deleteById(id)` per eligible booking (repeated for each Prescription the booking has).

### ExternalRecordReference (`com.cms.clinical.ExternalRecordReference`)
- Deletion target. Zero-or-more per booking (036/032).
- Deleted via `externalRecordReferenceRepository.deleteById(id)` per eligible booking.

## New query

`BookingRepository.findRetentionEligibleBookings(Instant retentionCutoff): List<Booking>`

```sql
-- JPQL, conceptually:
SELECT b FROM Booking b
WHERE b.patient.anonymizedAt IS NOT NULL
AND b.createdAt < :retentionCutoff
```

Returns every booking meeting both purge conditions, regardless of booking/slot status (research.md R4) — a cancelled or completed booking's clinical content is equally subject to the retention window.

## No state machine

This feature introduces no new entity and no new status/lifecycle field. The "purge has happened" state is implicit: the absence of `ConsultationNote`/`Prescription`/`ExternalRecordReference` rows for a booking, checked by simple existence at run time (`findByBooking_Id` style reads already used by 030/031/032), which is what makes the sweep naturally idempotent (re-querying `findRetentionEligibleBookings` and re-attempting delete on a booking with nothing left to delete is a safe no-op).
