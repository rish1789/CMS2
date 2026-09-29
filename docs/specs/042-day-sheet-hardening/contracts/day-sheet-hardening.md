# Contracts: Day Sheet Hardening

Both endpoints below already exist (documented in `specs/041-staff-console-pickers/contracts/staff-console-pickers.md`); this feature extends them additively. Authentication/authorization requirements (staff JWT, clinic-scoped `403`/`401`, doctor self-scoping) are unchanged from that existing contract and are not repeated here except where a new query param interacts with them.

## `GET /api/v1/clinics/{clinicId}/sessions?from={date}&to={date}&doctorProfileId={uuid}&page={n}&size={n}`

`from`/`to` unchanged (still default to today / today+14). **New**, both optional:
- `doctorProfileId` — filters results to one doctor. Must be a doctor with an active Role Assignment at this clinic; an id that doesn't match anything in the window returns an empty `sessions` page (not an error).
- `page` (default `0`), `size` (default `20`) — standard offset pagination.

Doctor self-scoping is unchanged: a caller whose only active role at this clinic is Doctor sees only their own sessions regardless of `doctorProfileId` (their own id is the only value that can return anything).

### Success — `200 OK`
```json
{
  "sessions": [
    {
      "sessionId": "uuid",
      "doctorProfileId": "uuid",
      "doctorName": "string",
      "sessionDate": "2026-09-10",
      "mode": "FIXED_TIME|QUEUE",
      "bookedSlotCount": 6,
      "totalSlotCount": 10
    }
  ],
  "doctors": [{ "doctorProfileId": "uuid", "name": "string", "staffCode": "string" }],
  "page": 0,
  "pageSize": 20,
  "totalCount": 47
}
```

- `bookedSlotCount`/`totalSlotCount`: `totalSlotCount` may be `0` (no slots generated yet) — the frontend must not render this as a fraction (see spec Edge Cases).
- `doctors`: every doctor with at least one session in the current `from`/`to` window, independent of `page`/`size`/`doctorProfileId` — always the complete set, so the filter dropdown never has to guess or re-fetch as the user pages through results. For a Doctor-self-scoped caller, this list contains at most their own entry.

## `GET /api/v1/clinics/{clinicId}/sessions/{sessionId}/day-sheet`

Unchanged request shape and authorization (including the Doctor-self-scoping 404-not-403 behavior for another doctor's session).

### Success — `200 OK`
```json
{
  "sessionId": "uuid",
  "doctorProfileId": "uuid",
  "doctorName": "string",
  "sessionDate": "2026-09-10",
  "mode": "FIXED_TIME",
  "slots": [
    {
      "slotId": "uuid",
      "startTime": "09:00:00",
      "endTime": "09:15:00",
      "tokenNumber": null,
      "status": "OPEN|BOOKED|COMPLETED|NO_SHOW",
      "isBuffer": false,
      "booking": null
    }
  ]
}
```

`doctorName`/`sessionDate` are the only additions (**new**, FR-010) — everything else in this response is unchanged.

## Not a contract change: whole-session cancellation

`POST /api/v1/clinics/{clinicId}/sessions/{sessionId}/cancel` (existing, from 029-whole-day-session-cancellation) is untouched by this feature. FR-001/002/003's confirmation step is a frontend-only addition in front of the existing call — no new request field, no new response field, no new error code.
