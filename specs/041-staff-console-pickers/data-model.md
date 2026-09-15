# Phase 1 Data Model: Clinic Staff Console — Browse & Pick Instead of Type-an-ID

No new database entity and no Flyway migration — every field these lists need already exists on `Clinic`, `RoleAssignment`, `Session`, `Slot`, `Booking`, `Patient`, `DoctorProfile`, `Account`. This feature adds read-only response DTOs only.

## New response shapes

### `ClinicMembershipResponse` (FR-001, `identity.account`)
| Field | Type | Source |
|---|---|---|
| `clinicId` | UUID | `RoleAssignment.clinic.id` |
| `name` | String | `RoleAssignment.clinic.name` |
| `address` | String | `RoleAssignment.clinic.address` |
| `role` | String (`ClinicAdmin`\|`Doctor`\|`Operations`) | `RoleAssignment.role` |

One row per active `RoleAssignment` the calling Account holds.

### `SessionSummaryResponse` (FR-003, `scheduling`)
| Field | Type | Source |
|---|---|---|
| `sessionId` | UUID | `Session.id` |
| `doctorProfileId` | UUID | `Session.doctorProfile.id` |
| `doctorName` | String | `Session.doctorProfile.account.name` |
| `sessionDate` | LocalDate | `Session.sessionDate` |
| `mode` | String (`FIXED_TIME`\|`QUEUE`) | `Session.mode` |

One row per `Session` at the clinic within the fixed 14-day window (today through today+14, FR-008).

### `SessionDaySheetResponse` (FR-004/FR-005, `booking`)
| Field | Type | Source |
|---|---|---|
| `sessionId` | UUID | `Session.id` |
| `doctorProfileId` | UUID | `Session.doctorProfile.id` — needed by the frontend to build the "Book"/"Book into queue" URLs (`?doctorProfileId=`) without a second fetch |
| `mode` | String | `Session.mode` |
| `slots` | `SlotDetail[]` | see below |

**`SlotDetail`**
| Field | Type | Source |
|---|---|---|
| `slotId` | UUID | `Slot.id` |
| `startTime` | LocalTime, nullable | `Slot.startTime` (null for Queue-mode) |
| `endTime` | LocalTime, nullable | `Slot.endTime` |
| `tokenNumber` | Integer, nullable | `Slot.tokenNumber` (Queue-mode only) |
| `status` | String | `Slot.status` |
| `isBuffer` | boolean | `Slot.isBuffer` — the Clarify-resolved marker the frontend uses to show "Reserved capacity" and omit the Book action (research.md R6) |
| `booking` | `BookingDetail`, nullable | present only when an ACTIVE `Booking` exists for this slot |

**`BookingDetail`**
| Field | Type | Source |
|---|---|---|
| `bookingId` | UUID | `Booking.id` |
| `patientId` | UUID | `Booking.patient.id` |
| `patientName` | String | `Booking.patient.name` |

### `DoctorSummaryResponse` (FR-006, `identity.doctor`)
| Field | Type | Source |
|---|---|---|
| `doctorProfileId` | UUID | `DoctorProfile.id` |
| `name` | String | `DoctorProfile.account.name` |
| `specialization` | String | `DoctorProfile.specialization` |

### `StaffSummaryResponse` (FR-006a, `identity.staff`)
| Field | Type | Source |
|---|---|---|
| `accountId` | UUID | `RoleAssignment.account.id` |
| `name` | String | `RoleAssignment.account.name` |
| `role` | String | `RoleAssignment.role` |

### `PatientSearchResultResponse` (FR-007, `patient.record`)
| Field | Type | Source |
|---|---|---|
| `patientId` | UUID | `Patient.id` |
| `name` | String | `Patient.name` |
| `phone` | String, nullable | `Patient.phone` |

## Relationships / lifecycle

No new relationships — every DTO above is a read projection over existing associations (`RoleAssignment → Account/Clinic`, `Session → DoctorProfile/Clinic`, `Slot → Session`, `Booking → Slot/Patient`, `DoctorProfile → Account`). No state transitions are introduced; this feature is entirely read-only (FR-010).
