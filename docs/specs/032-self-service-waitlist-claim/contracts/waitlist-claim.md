# Contract: Waitlist Claim / Decline (User Stories 1 & 2)

The expiry sweep (User Story 3) has **no** HTTP contract — it is only ever invoked internally by
`WaitlistExpirySweepTrigger`'s `@Scheduled` job (research.md R6).

## `POST /api/v1/patients/waitlist-entries/{entryId}/claim`

Requires a valid patient bearer token (`/api/v1/patients/**` chain). Ownership (not clinic
membership) is the access boundary — mirrors `/api/v1/patients/bookings/{bookingId}/cancel` (028).

### Request

```json
{ "appointmentTypeId": "uuid", "patientName": "string" }
```

### Success Response — `201 Created`

Identical shape to every other booking response (`com.cms.booking.dto.BookingResponse`):

```json
{
  "id": "uuid",
  "slotId": "uuid",
  "patientId": "uuid",
  "appointmentTypeId": "uuid",
  "lockedFee": 300.00,
  "paymentStatus": "PENDING",
  "status": "ACTIVE",
  "createdAt": "2026-09-04T10:00:00Z"
}
```

### Error Responses

| Status | Condition | Body `error` |
|---|---|---|
| `401 Unauthorized` | Missing/invalid patient bearer token | — |
| `404 Not Found` | No entry with `entryId`, or it doesn't belong to the caller | `WAITLIST_ENTRY_NOT_FOUND` |
| `409 Conflict` | Entry is not currently `OFFERED`, or its window has lapsed | `WAITLIST_OFFER_NOT_CLAIMABLE` |
| `404 Not Found` | `appointmentTypeId` doesn't exist | `APPOINTMENT_TYPE_NOT_FOUND` |
| `409 Conflict` | No fee configured for that doctor/appointment type combination | `NO_FEE_CONFIGURED` |
| `409 Conflict` | The offered Slot was already booked ordinarily before this claim (research.md R4) — the entry is expired and the next eligible entry is re-offered as a side effect | `SLOT_ALREADY_BOOKED` |

## `POST /api/v1/patients/waitlist-entries/{entryId}/decline`

Requires a valid patient bearer token. Same ownership boundary as claim.

### Request

No body.

### Success Response — `200 OK`

```json
{
  "id": "uuid",
  "clinicId": "uuid",
  "doctorProfileId": "uuid | null",
  "specialization": "string | null",
  "status": "EXPIRED",
  "joinedAt": "2026-09-04T09:00:00Z"
}
```

(Same `WaitlistEntryResponse` shape 028's join endpoints already return.)

### Error Responses

| Status | Condition | Body `error` |
|---|---|---|
| `401 Unauthorized` | Missing/invalid patient bearer token | — |
| `404 Not Found` | No entry with `entryId`, or it doesn't belong to the caller | `WAITLIST_ENTRY_NOT_FOUND` |
| `409 Conflict` | Entry is not currently `OFFERED` | `WAITLIST_OFFER_NOT_CLAIMABLE` |

## Contract Invariants (traced to spec)

- A successful claim always produces exactly one new Booking, fee resolved and locked at that
  moment (FR-001/FR-002, SC-001).
- A claim or decline against an entry the caller doesn't own is always rejected without revealing
  whether the entry exists (FR-003, research.md R8).
- Declining and a lapsed-window claim attempt are both terminal for that entry — never
  re-claimable afterward (FR-004).
- Under a race between claim/decline/expiry on the same entry, exactly one ever succeeds
  (FR-010/SC-004).
