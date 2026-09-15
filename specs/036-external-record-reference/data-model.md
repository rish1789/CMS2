# Data Model: External Record Reference

## `ExternalRecordReference` (new)

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `booking` | `Booking` (`@ManyToOne`, not null) | No uniqueness — a booking may have zero or more (research.md R3/plan.md, mirrors 031). |
| `doctorProfile` | `DoctorProfile` (`@ManyToOne`, not null) | The authoring (treating) doctor. |
| `recordType` | `String`, not null | e.g. "Lab result", "Imaging report", "Prior diagnosis". |
| `sourceProvider` | `String`, not null | The external source/provider name. |
| `recordDate` | `LocalDate`, not null | The external record's own date, as recorded by the doctor (research.md R4 — no value constraint). |
| `summary` | `String` (`TEXT`), not null | Free text. |
| `createdAt` | `Instant`, not null | Stamped at construction. |

No file/document field of any kind (research.md R5). No setters beyond what JPA needs internally
— no mutation path exists.

### Migration

`V21__create_external_record_reference.sql`:

```sql
CREATE TABLE external_record_reference (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id        UUID NOT NULL REFERENCES booking (id),
    doctor_profile_id UUID NOT NULL REFERENCES doctor_profile (id),
    record_type       VARCHAR(255) NOT NULL,
    source_provider   VARCHAR(255) NOT NULL,
    record_date       DATE NOT NULL,
    summary           TEXT NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL
);
```

(No `UNIQUE` on `booking_id` — deliberately, mirrors 031's `prescription` table.)

## `Booking`/`Slot`/`Session`/`DoctorProfile` (existing) — read-only

Identical read pattern to 030/031.

## `ExternalRecordReferenceRepository`

- `findByBooking_Id(UUID bookingId): List<ExternalRecordReference>` — every reference for a
  booking (plural, mirrors 031's own shape).

## Reused unchanged: `TreatingDoctorAuthorizationService` (030/031)

No changes — this feature is its third caller (research.md R2).
