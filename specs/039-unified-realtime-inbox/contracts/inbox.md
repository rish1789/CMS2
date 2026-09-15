# Contract: Unified Real-Time Inbox

Requires a valid staff bearer token (`/api/v1/clinics/**` chain). A missing/invalid token returns
`401`. Every endpoint additionally requires the caller to be an active `Operations` or
`ClinicAdmin` at `clinicId` (research.md R6) — otherwise `403 FORBIDDEN`.

## `GET /api/v1/clinics/{clinicId}/inbox`

Returns every non-`RESOLVED` Inbox Item at this clinic, oldest-unclaimed-first (spec Assumptions).

### Success Response — `200 OK`

```json
[
  {
    "id": "uuid",
    "itemType": "WALK_IN",
    "status": "UNCLAIMED",
    "claimedByAccountId": null,
    "claimedByName": null,
    "createdAt": "2026-09-05T10:00:00Z",
    "summary": {
      "bookingId": "uuid",
      "patientName": "Jane Doe",
      "slotStartTime": "2026-09-05T10:15:00Z"
    }
  },
  {
    "id": "uuid",
    "itemType": "WAITLIST_OFFER",
    "status": "CLAIMED",
    "claimedByAccountId": "uuid",
    "claimedByName": "Front Desk A",
    "createdAt": "2026-09-05T09:50:00Z",
    "summary": {
      "waitlistEntryId": "uuid",
      "patientContact": "john@example.com",
      "offerExpiresAt": "2026-09-05T10:20:00Z"
    }
  },
  {
    "id": "uuid",
    "itemType": "DEVERIFICATION_CASCADE",
    "status": "UNCLAIMED",
    "claimedByAccountId": null,
    "claimedByName": null,
    "createdAt": "2026-09-05T09:00:00Z",
    "summary": {
      "doctorName": "Dr. Rao",
      "cancelledBookingCount": 4
    }
  }
]
```

`summary`'s shape is `itemType`-dependent (research.md R4/data-model.md), read live from the
referenced `Booking`/`WaitlistEntry` at response time, reflecting any anonymization already
applied (spec FR-016). `WALK_IN`'s `patientName` comes from the clinic-scoped `Patient` record
(`Booking.patient.name`, scrubbed by 033). `WAITLIST_OFFER` has no equivalent name field to show —
`PatientAccount` (the only identity a `WaitlistEntry` references) carries no `name` field anywhere
in this system (the same gap 021 already found and worked around) — so its summary instead shows
`patientContact` (`WaitlistEntry.patientAccount.email`), which is not currently scrubbed by any
anonymization path (033 only touches the clinic-scoped `Patient`, not `PatientAccount`).

## `GET /api/v1/clinics/{clinicId}/inbox/stream`

Server-Sent Events stream (`Content-Type: text/event-stream`). Emits one event per Inbox Item
create/claim/release/resolve at this clinic, each carrying the same item shape as the list
endpoint above (event name `inbox-item`). No historical backlog is replayed on connect — callers
should call the list endpoint once on load, then apply stream events incrementally (research.md
R1/R2). Consumed via `fetch` streaming, not `EventSource` (research.md R2), so the existing
`Authorization` header works unchanged.

## `POST /api/v1/clinics/{clinicId}/inbox/{itemId}/claim`

### Success Response — `200 OK`

Returns the updated item (same shape as above, `status: "CLAIMED"`, `claimedByAccountId` set to
the caller).

### Error Responses

| Status | Condition | Body `error` |
|---|---|---|
| `404 Not Found` | No Inbox Item with `itemId` at `clinicId` | `INBOX_ITEM_NOT_FOUND` |
| `409 Conflict` | Item is already `CLAIMED` or `RESOLVED` (FR-008) | `ALREADY_CLAIMED` |

## `POST /api/v1/clinics/{clinicId}/inbox/{itemId}/release`

Only the current claimant may release (FR-010).

### Success Response — `200 OK`

Returns the updated item (`status: "UNCLAIMED"`, `claimedByAccountId: null`).

### Error Responses

| Status | Condition | Body `error` |
|---|---|---|
| `404 Not Found` | No Inbox Item with `itemId` at `clinicId` | `INBOX_ITEM_NOT_FOUND` |
| `409 Conflict` | Caller is not the current claimant, or item isn't `CLAIMED` | `NOT_CLAIMANT` |

## `POST /api/v1/clinics/{clinicId}/inbox/{itemId}/resolve`

Only the current claimant may resolve (FR-011).

### Success Response — `200 OK`

Returns the updated item (`status: "RESOLVED"`).

### Error Responses

| Status | Condition | Body `error` |
|---|---|---|
| `404 Not Found` | No Inbox Item with `itemId` at `clinicId` | `INBOX_ITEM_NOT_FOUND` |
| `409 Conflict` | Caller is not the current claimant, or item isn't `CLAIMED` | `NOT_CLAIMANT` |

## Contract Invariants (traced to spec)

- Two concurrent claim attempts on the same item never both succeed — exactly one `200`, one `409
  ALREADY_CLAIMED` (SC-002, FR-008).
- A `GET .../inbox` response never includes an item scoped to a different `clinicId` (SC-003,
  FR-006).
- Every item created by 020/029/008 (FR-002/003/004) is visible via the list endpoint and pushed
  via the stream endpoint within the same request that created it — no polling delay (SC-001).
- A `RESOLVED` item never appears in the list endpoint's response again (FR-012), though its row
  and history remain queryable at the data layer for audit purposes.
- A `WAITLIST_OFFER` item transitions to `RESOLVED` automatically when its underlying offer is
  claimed, declined, or expires, with no client call to `/resolve` required (FR-013).
