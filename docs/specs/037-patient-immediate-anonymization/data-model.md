# Data Model: Patient Immediate Anonymization

## `Patient` (extend, 009)

New field:

| Field | Type | Notes |
|---|---|---|
| `anonymizedAt` | `Instant`, nullable | `null` = not yet anonymized. Set once via `anonymize()`, never changed again (research.md R4). |

New method:

- `anonymize()` — if `anonymizedAt == null`: sets `name = "Anonymized Patient"`, `phone = null`,
  `anonymizedAt = Instant.now()`. If already non-null, a no-op (idempotent, FR-007).
- `isAnonymized()` — `anonymizedAt != null`, the durable marker 034 will check.

No change to `clinic`, `patientAccount` (untouched, FR-006), `createdAt`.

### Migration

`V22__patient_anonymized_at.sql`:

```sql
ALTER TABLE patient ADD COLUMN anonymized_at TIMESTAMPTZ;
```

## `BookingRepository` addition

- `existsActiveFutureBookingForPatient(UUID patientId): boolean` — via
  `existsByPatient_IdAndStatusAndSlot_Status(patientId, BookingStatus.ACTIVE, SlotStatus.BOOKED)`
  (a derived query, research.md R2).

## `Booking`/`Slot` (existing) — read-only

This feature reads `Booking.status`/`Slot.status` to determine the block precondition; nothing
about either is written by this feature.
