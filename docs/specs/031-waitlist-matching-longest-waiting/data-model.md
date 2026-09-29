# Data Model: Waitlist Matching (Longest-Waiting, Doctor/Specialization)

## New Entity: `WaitlistEntry` (`com.cms.waitlist`)

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | `UUID` | No | PK |
| `clinic` | `Clinic` (`@ManyToOne`) | No | Multi-tenancy scope (Constitution). |
| `patientAccount` | `PatientAccount` (`@ManyToOne`) | No | Required — matching must be able to notify whoever is offered (Assumptions). |
| `doctorProfile` | `DoctorProfile` (`@ManyToOne`) | Yes | Non-null = doctor-match tier entry. Null = specialization-only tier entry. |
| `specialization` | `String` | Yes | Populated only when `doctorProfile` is null (R2) — a doctor-match entry's effective specialization is read live from `doctorProfile.getSpecialization()`. |
| `status` | `WaitlistEntryStatus` (`WAITING`/`OFFERED`) | No | Defaults `WAITING` at join. |
| `joinedAt` | `Instant` | No | Set at creation — the sole ordering key within a tier (FR-005). |
| `offeredAt` | `Instant` | Yes | Set together with `offerExpiresAt` when matched. |
| `offerExpiresAt` | `Instant` | Yes | `offeredAt + 30 minutes` (R5) — the claim-window deadline a later feature (029) will enforce. |

**Migration**: `V17__create_waitlist_entry.sql` —
```sql
CREATE TABLE waitlist_entry (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id            UUID NOT NULL REFERENCES clinic (id),
    patient_account_id   UUID NOT NULL REFERENCES patient_account (id),
    doctor_profile_id    UUID REFERENCES doctor_profile (id),
    specialization       VARCHAR(255),
    status               VARCHAR(20) NOT NULL,
    joined_at            TIMESTAMPTZ NOT NULL,
    offered_at           TIMESTAMPTZ,
    offer_expires_at     TIMESTAMPTZ
);
```

No uniqueness/exclusion constraint — duplicate entries for the same patient/doctor are explicitly
allowed (spec Edge Cases, R6).

## New Enum: `WaitlistEntryStatus` (`com.cms.waitlist`)

`WAITING`, `OFFERED` — a later feature (029) will extend this in place with `CLAIMED`/`EXPIRED`,
mirroring this codebase's established "extend the enum when the next feature needs to" pattern
(`SlotStatus`, `NotificationEventStatus`).

## Computation (`WaitlistMatchingService.matchAndOffer(Session session, Slot slot)`)

Called only from `WaitlistBumpListener` (R3), never directly:

1. Tier 1: `findFirstByClinic_IdAndDoctorProfile_IdAndStatusOrderByJoinedAtAsc(clinic.id,
   doctorProfile.id, WAITING)`. If present → offer it (step 3).
2. Tier 2: only if tier 1 was empty —
   `findFirstByClinic_IdAndSpecializationAndDoctorProfileIsNullAndStatusOrderByJoinedAtAsc(clinic.id,
   doctorProfile.getSpecialization(), WAITING)`. If present → offer it (step 3).
3. Offer: set `status = OFFERED`, `offeredAt = now`, `offerExpiresAt = now + 30min`; publish a
   notification to the entry's Patient Account (R5). If neither tier found anything, no-op
   (FR-007).

## Validation Rules (from Functional Requirements)

- FR-001/FR-002: join requires either a `doctorProfileId` or a `specialization` string, never
  ambiguously both/neither in a way that leaves the tier undetermined — exactly one of
  `doctorProfile`/`specialization` ends up set on the created entry.
- FR-003: every entry always has `clinic`, `patientAccount`, `joinedAt` set; exactly one of
  `doctorProfile`/`specialization` (R2).
- FR-004/FR-009: the matching search runs only from `WaitlistBumpListener`, itself only invoked by
  `BookingCancelledEvent` (025's sole publisher) — no other code path calls
  `WaitlistMatchingService`.
- FR-005/FR-006/SC-001/SC-002: R4's two-query, tier-1-first structure.
- FR-007/SC-004: neither query returning anything is a silent no-op, not an error.
- FR-008/SC-005: a successful match transitions exactly one entry to `OFFERED` and publishes
  exactly one notification.
- FR-010: both queries filter `status = WAITING` — an `OFFERED` entry is structurally excluded
  from ever being matched again.
