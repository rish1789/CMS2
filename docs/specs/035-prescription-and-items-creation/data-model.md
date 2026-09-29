# Data Model: Prescription + Items Creation

## `Prescription` (new)

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `booking` | `Booking` (`@ManyToOne`, not null) | No `UNIQUE` constraint — a booking may have zero or more (research.md R3), unlike `ConsultationNote`'s `@OneToOne`. |
| `doctorProfile` | `DoctorProfile` (`@ManyToOne`, not null) | The authoring (treating) doctor. |
| `items` | `List<PrescriptionItem>` (`@OneToMany`, cascade `PERSIST` only) | At least one, enforced in the service before any write (research.md R4). |
| `createdAt` | `Instant`, not null | Stamped at construction. |

No setters beyond what JPA needs internally, no `items` mutator beyond what the constructor
populates — no mutation path exists (research.md, mirrors 030's R7).

## `PrescriptionItem` (new)

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `prescription` | `Prescription` (`@ManyToOne`, not null) | |
| `medicationName` | `String`, not null | |
| `dosage` | `String`, not null | |
| `frequency` | `String`, not null | |
| `duration` | `String`, not null | |
| `instructions` | `String`, nullable | research.md R5 |

### Migration

`V20__create_prescription.sql`:

```sql
CREATE TABLE prescription (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id        UUID NOT NULL REFERENCES booking (id),
    doctor_profile_id UUID NOT NULL REFERENCES doctor_profile (id),
    created_at        TIMESTAMPTZ NOT NULL
);

CREATE TABLE prescription_item (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prescription_id   UUID NOT NULL REFERENCES prescription (id),
    medication_name   VARCHAR(255) NOT NULL,
    dosage            VARCHAR(255) NOT NULL,
    frequency         VARCHAR(255) NOT NULL,
    duration          VARCHAR(255) NOT NULL,
    instructions      TEXT
);
```

(No `UNIQUE` on `prescription.booking_id` — deliberately, research.md R3.)

## `Booking`/`Slot`/`Session`/`DoctorProfile` (existing) — read-only

Identical read pattern to 030 — this feature reads `Booking.getSlot().getSession()
.getDoctorProfile()` to determine the treating doctor; nothing about any of these entities is
written by this feature.

## `PrescriptionRepository`

- `findByBooking_Id(UUID bookingId): List<Prescription>` — every Prescription for a booking
  (plural, unlike 030's singular `Optional`, per R3's cardinality difference).
- `findById(UUID id): Optional<Prescription>` (inherited) — a single Prescription by its own id,
  for the get-one-by-id read path.

## Shared: `TreatingDoctorAuthorizationService` (new, extracted from 030)

- `findBookingInClinic(UUID clinicId, UUID bookingId): Booking`
- `requireTreatingDoctor(Booking booking, UUID callerAccountId): DoctorProfile`

Both `ConsultationNoteService` (030, refactored) and `PrescriptionService` (this feature) call
these instead of maintaining their own private copies (research.md R2).
