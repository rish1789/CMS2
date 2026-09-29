# Data Model: Patient Context & Clinical History Hub

No new persisted entity, table, or column — this feature reads existing `Patient`/`Booking`/`Slot`/`Session` data and exposes one already-persisted field (`Patient.anonymizedAt`) that no response DTO currently carries.

## Extended: `PatientSearchResultResponse`

| Field | Type | Notes |
|-------|------|-------|
| `patientId` | `UUID` | unchanged |
| `name` | `String` | unchanged (already scrubbed to `"Anonymized Patient"` post-anonymization) |
| `phone` | `String \| null` | unchanged (already `null` post-anonymization) |
| `anonymizedAt` | `Instant \| null` | **NEW** — `Patient.anonymizedAt` verbatim; `null` means not anonymized |

## New: `PatientBookingSummaryResponse`

One row per booking, returned as a paginated list by the new endpoint.

| Field | Type | Source |
|-------|------|--------|
| `bookingId` | `UUID` | `Booking.id` |
| `sessionDate` | `LocalDate` | `Booking.slot.session.sessionDate` |
| `startTime` | `LocalTime \| null` | `Booking.slot.startTime` (null for Queue-mode) |
| `doctorName` | `String` | `Booking.slot.session.doctorProfile.account.name` |
| `appointmentTypeName` | `String` | `Booking.appointmentType.name` |
| `bookingStatus` | `'ACTIVE' \| 'CANCELLED'` | `Booking.status` |
| `slotStatus` | `'OPEN' \| 'BOOKED' \| 'COMPLETED' \| 'NO_SHOW'` | `Booking.slot.status` |

```java
public record PatientBookingSummaryResponse(
    UUID bookingId, LocalDate sessionDate, LocalTime startTime, String doctorName,
    String appointmentTypeName, String bookingStatus, String slotStatus) {
  public static PatientBookingSummaryResponse from(Booking booking) { ... }
}

public record PatientBookingHistoryResponse(
    List<PatientBookingSummaryResponse> bookings, int page, int pageSize, long totalCount) {}
```

## New repository method: `BookingRepository.findByPatient_IdOrderBySlot_Session_SessionDateDesc`

```java
Page<Booking> findByPatient_IdOrderBySlot_Session_SessionDateDesc(UUID patientId, Pageable pageable);
```

Mirrors the existing `findByPatient_PatientAccount_IdOrderByCreatedAtDesc` (021) — same derived-query shape, different scoping relationship (`patient.id` — clinic-scoped by construction — instead of `patient.patientAccount.id`, which spans clinics).

## Frontend types (`frontend/src/features/patient-search/api.ts`)

```ts
export interface PatientSearchResult {
  patientId: string
  name: string
  phone: string | null
  anonymizedAt: string | null   // NEW
}

export interface PatientBookingSummary {
  bookingId: string
  sessionDate: string
  startTime: string | null
  doctorName: string
  appointmentTypeName: string
  bookingStatus: 'ACTIVE' | 'CANCELLED'
  slotStatus: 'OPEN' | 'BOOKED' | 'COMPLETED' | 'NO_SHOW'
}

export interface PatientBookingHistoryResult {
  bookings: PatientBookingSummary[]
  page: number
  pageSize: number
  totalCount: number
}
```
