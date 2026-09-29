# Phase 1 Data Model: Patient Clinical Record Access

No new entity, table, or column. This feature adds new *read paths* to three existing entities and
one new derived-query method on an existing repository. Nothing about how these entities are
created, structured, or made immutable changes.

## Existing entities touched (read-only, unchanged shape)

| Entity | Existing cardinality per Booking | New read path added |
|---|---|---|
| `ConsultationNote` | 0 or 1 (`UNIQUE` on `booking_id`) | `ConsultationNoteService.getForPatient(bookingId, patientAccountId)` |
| `Prescription` (+ `PrescriptionItem`) | 0 or many | `PrescriptionService.listForPatient(bookingId, patientAccountId)` |
| `ExternalRecordReference` | 0 or many | `ExternalRecordReferenceService.listForPatient(bookingId, patientAccountId)` |

Each new method:
1. Loads the booking scoped to the caller's own patient account (`BookingRepository.findByIdAndPatient_PatientAccount_Id`) — absent means either the booking doesn't exist or isn't the caller's; both cases are refused identically (`BookingNotFoundException`, 404), so a caller can never distinguish "wrong id" from "someone else's booking" (no enumeration signal).
2. If found, looks up the record(s) via the exact same repository method the treating-doctor path already uses (`findByBooking_Id`) — no new query logic, no new filtering.

No authorization decision here ever consults `TreatingDoctorAuthorizationService` — that class's
entire contract (treating-doctor identity trace) is orthogonal to and untouched by this feature.
The patient-facing methods use a parallel, independent check (patient-account ownership) against
the same `Booking`.

## New repository method

```
// backend/src/main/java/com/cms/booking/repository/BookingRepository.java
Optional<Booking> findByIdAndPatient_PatientAccount_Id(UUID id, UUID patientAccountId);
```

A derived Spring Data query, same style as the existing
`findByPatient_PatientAccount_IdOrderByCreatedAtDesc` on the same repository/entity path.

## New availability-check surface (bulk, `clinical` module — research.md Decision 3)

```
ClinicalRecordAvailabilityService.findBookingIdsWithAnyRecord(
    Collection<UUID> bookingIds, UUID patientAccountId) -> Set<UUID>
```

Backed by three new bulk queries (one per clinical repository), each of the shape:

```
SELECT DISTINCT n.booking.id FROM ConsultationNote n
WHERE n.booking.id IN :bookingIds
AND n.booking.patient.patientAccount.id = :patientAccountId
```

(equivalently for `Prescription`/`ExternalRecordReference`), unioned in the service layer into one
`Set<UUID>`. The `patientAccountId` scoping in the query itself — not just at the booking-lookup
step — means even a caller who somehow supplies another patient's booking ids gets no signal about
whether those ids have records; they're silently excluded from the result set, never included and
never separately flagged as "exists but not yours."

## State transitions

None — this feature adds no new state to any entity. The three record types' existing lifecycle
(created once by the treating doctor, immutable thereafter, eventually hard-deleted by the DPDP
retention purge) is entirely unchanged; this feature only adds who may subsequently read what
already exists at read time.
