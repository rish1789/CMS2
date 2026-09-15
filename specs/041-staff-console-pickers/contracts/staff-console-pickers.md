# Contracts: Clinic Staff Console Pickers

All six endpoints require a valid staff JWT (`Authorization: Bearer <token>`, existing `/api/v1/clinics/**` staff chain) — no new authentication mechanism. Every endpoint scoped to a specific `{clinicId}` returns `403 FORBIDDEN` if the caller has no active `RoleAssignment` at that clinic (the same check every existing action endpoint in this codebase already performs) and `401 UNAUTHORIZED` for a missing/invalid token (existing `StaffAuthenticationEntryPoint`).

## `GET /api/v1/clinics/mine`

No `{clinicId}` — scoped to whichever Account the bearer token identifies. Cannot 403 (there is no "clinic" to be forbidden from — an account with zero active roles simply gets an empty list).

### Success — `200 OK`
```json
{ "clinics": [{ "clinicId": "uuid", "name": "string", "address": "string", "role": "ClinicAdmin|Doctor|Operations" }] }
```

## `GET /api/v1/clinics/{clinicId}/sessions?from={date}&to={date}`

`from`/`to` optional — default to today and today+14 respectively (FR-008); the frontend never sends a different range in v1.

### Success — `200 OK`
```json
{ "sessions": [{ "sessionId": "uuid", "doctorProfileId": "uuid", "doctorName": "string", "sessionDate": "2026-09-10", "mode": "FIXED_TIME|QUEUE" }] }
```

## `GET /api/v1/clinics/{clinicId}/sessions/{sessionId}/day-sheet`

### Success — `200 OK`
```json
{
  "sessionId": "uuid",
  "doctorProfileId": "uuid",
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
    },
    {
      "slotId": "uuid",
      "startTime": "09:15:00",
      "endTime": "09:30:00",
      "tokenNumber": null,
      "status": "BOOKED",
      "isBuffer": false,
      "booking": { "bookingId": "uuid", "patientId": "uuid", "patientName": "string" }
    }
  ]
}
```

### Errors
| Status | Condition |
|---|---|
| `404` | `sessionId` doesn't exist or doesn't belong to `clinicId` |

## `GET /api/v1/clinics/{clinicId}/doctors`

### Success — `200 OK`
```json
{ "doctors": [{ "doctorProfileId": "uuid", "name": "string", "staffCode": "string", "specialization": "string" }] }
```

## `GET /api/v1/clinics/{clinicId}/staff`

### Success — `200 OK`
```json
{ "staff": [{ "roleAssignmentId": "uuid", "accountId": "uuid", "name": "string", "staffCode": "string", "role": "ClinicAdmin|Doctor|Operations", "email": "string", "mobile": "string|null", "specialization": "string|null", "experienceYears": "number|null", "joinedAt": "ISO-8601 instant", "active": "boolean" }] }
```

## `GET /api/v1/clinics/{clinicId}/patients/search?q={term}`

`q` required, minimum 2 characters (avoids a near-full-table scan on a 1-character query). Already-anonymized patients (037) are never returned, regardless of match (research.md R4) — nothing in this or any other tool can act usefully on one.

### Success — `200 OK`
```json
{ "patients": [{ "patientId": "uuid", "name": "string", "phone": "string|null" }] }
```
Empty `patients` array (not an error) when nothing matches (FR-009, SC-003).

### Errors
| Status | Condition |
|---|---|
| `400` | `q` missing or under 2 characters |

## Contract Invariants (traced to spec)

- Every clinic-scoped endpoint above enforces the exact same "active role at this clinic" check every existing action endpoint already enforces — no new authorization concept (FR-010).
- The day-sheet response's `isBuffer` field is what the frontend uses to withhold the "Book" action on that row (FR-005, R6) — never filtered server-side, since staff can still see a buffer slot exists.
- None of these six endpoints can create, modify, or delete anything — GET only, read-only (FR-010).
