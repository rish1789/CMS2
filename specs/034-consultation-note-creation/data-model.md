# Data Model: Consultation Note Creation

## `ConsultationNote` (new)

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `booking` | `Booking` (`@OneToOne`, not null) | The visit this note documents. `UNIQUE` — the actual one-note-per-booking guarantee (research.md R2). |
| `doctorProfile` | `DoctorProfile` (`@ManyToOne`, not null) | The authoring (treating) doctor — always equal to `booking.getSlot().getSession().getDoctorProfile()` at creation time. |
| `content` | `String` (`TEXT`, not null) | Free text (research.md R4). |
| `createdAt` | `Instant`, not null | Stamped at construction. |

No `offeredAt`-style mutable fields, no setters beyond what JPA needs internally — this entity
has no mutation path at all (research.md R7).

### Migration

`V19__create_consultation_note.sql`:

```sql
CREATE TABLE consultation_note (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id        UUID NOT NULL UNIQUE REFERENCES booking (id),
    doctor_profile_id UUID NOT NULL REFERENCES doctor_profile (id),
    content           TEXT NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL
);
```

## `Booking`/`Slot`/`Session`/`DoctorProfile` (existing) — read-only

This feature reads `Booking.getSlot().getSession().getDoctorProfile()` to determine the treating
doctor; nothing about any of these entities is written by this feature.

## `ConsultationNoteRepository`

- `findByBooking_Id(UUID bookingId): Optional<ConsultationNote>` — the sole query this feature
  needs (get-by-booking); creation relies on `saveAndFlush` + the table's own `UNIQUE` constraint
  for its actual race-closure (research.md R2), not a separate existence-check query.
