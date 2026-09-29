# Contracts: Booking Protection / Appointment Abuse Prevention

## 1. Existing patient booking endpoints — new failure responses

No new endpoint; both existing self-service booking-creation endpoints gain two new possible
failure responses, checked in the order fixed by research.md Decision 6 (rate limit first).

- `POST /api/v1/patients/clinics/{clinicId}/slots/{slotId}/book` (fixed-time)
- `POST /api/v1/patients/clinics/{clinicId}/sessions/{sessionId}/queue-bookings` (queue)

Both realms already require a valid patient JWT (`Authorization: Bearer ...`) — unchanged.

| Status | Body shape | When |
|---|---|---|
| `429 Too Many Requests` | `{ "error": "RATE_LIMITED", "message": "<non-accusatory, states approx. wait time>", "retryAfterSeconds": <int> }` | Rate-limit threshold exceeded (FR-007, FR-010, FR-011); checked first — takes precedence over the limit-reached response below even if both would apply (Clarifications Q1). |
| `409 Conflict` | `{ "error": "BOOKING_LIMIT_REACHED", "message": "<clear, non-accusatory, states the limit was reached, no other-clinic detail>" }` | Active-appointment limit (global or per-clinic) would be exceeded (FR-002, FR-005). `409` chosen to match this codebase's existing convention for "the current state of your data conflicts with this request" (mirrors `SlotAlreadyBookedException`'s existing `409`), rather than introducing a new status code for a business-rule refusal. |
| `200`/existing success shape | unchanged | Attempt recorded as `SUCCESS`; every existing successful-booking response shape is unchanged. |

Neither new error body includes which specific limit, threshold, or signal applied (spec.md
NFR-006) — the two messages above are the *only* two states a patient ever sees.

---

## 2. ClinicAdmin — flag review (`/api/v1/clinics/{clinicId}/protection/flags`)

Staff realm (`/api/v1/clinics/**`), requires an active **ClinicAdmin** role at `{clinicId}`
specifically (research.md Decision 7) — Doctor/Operations-only callers get `403`.

### `GET /api/v1/clinics/{clinicId}/protection/flags`

Query params: `status` (`OUTSTANDING` | `RESOLVED`, optional, default `OUTSTANDING`), `patientAccountId`
(optional, exact match), `page`/`size` (optional, default `0`/`20` — matches this codebase's existing
pagination convention).

`200` response:

```json
{
  "flags": [
    {
      "id": "uuid",
      "patientAccountId": "uuid",
      "patientDisplayName": "string",
      "signalType": "HIGH_ATTEMPT_VOLUME | REPEATED_CANCELLATIONS | REPEATED_NO_SHOWS | OVERLAPPING_APPOINTMENTS | REPEATED_RATE_LIMIT_VIOLATIONS",
      "reason": "string",
      "detectedAt": "instant",
      "status": "OUTSTANDING | RESOLVED",
      "resolvedAt": "instant | null",
      "resolvedBy": "string | null"
    }
  ],
  "page": 0, "pageSize": 20, "totalCount": 0
}
```

Only flags with a non-null `clinicId` matching `{clinicId}`, plus (per patient, if that patient
currently sits at their global cap) one synthesized read-only fact row — see below.

### `GET /api/v1/clinics/{clinicId}/protection/flags/{flagId}`

`200`: the flag as above, plus a `recentActivity` block scoped to *this clinic only* (FR-021,
FR-022, BR-004):

```json
{
  "flag": { "...": "as above" },
  "recentActivity": {
    "recentBookings": [ "...same shape as existing PatientBookingSummaryResponse, this clinic only" ],
    "recentCancellations": [ "..." ],
    "recentNoShows": [ "..." ],
    "rateLimitViolations": [ { "occurredAt": "instant" } ],
    "globalActiveAppointmentCount": 0,
    "atGlobalLimit": false
  }
}
```

`globalActiveAppointmentCount`/`atGlobalLimit` are the one documented cross-clinic exception
(FR-022) — a count and a boolean, never another clinic's appointment details.

`404` if `flagId` doesn't belong to `{clinicId}` (mirrors this codebase's existing "not found, not
403" convention for cross-tenant lookups — e.g. `SessionDaySheetController`'s doctor-scoping fix
earlier this session).

### `POST /api/v1/clinics/{clinicId}/protection/flags/{flagId}/resolve`

No request body. `200` with the updated flag (now `status: RESOLVED`, `resolvedAt`/`resolvedBy`
populated from the caller). `409` if already resolved. `404` for a flag not at this clinic.

---

## 3. ClinicAdmin — per-clinic limit override (`/api/v1/clinics/{clinicId}/protection/limit-override`)

Same realm/role requirement as above.

### `GET .../limit-override`

`200`: `{ "maxActiveAppointments": int | null, "globalMax": int }` — `null` means no override set
(global limit only).

### `PUT .../limit-override`

Request: `{ "maxActiveAppointments": int }`. `200` with the updated value. `400` if
`maxActiveAppointments` exceeds the current global cap (BR-005) or isn't a positive integer.
`DELETE .../limit-override` removes the override (returns to global-only). Every `PUT`/`DELETE`
appends a `ClinicBookingLimitOverrideChangeLog` row (AUD-003).

### `GET .../limit-override/history`

`200`: array of `{ "previousMaxActiveAppointments": int | null, "newMaxActiveAppointments": int | null, "changedAt": "instant", "changedBy": "string" }`, newest first (AUD-003, AUD-004). Same role requirement as the rest of this section.

---

## 4. Super Admin — settings (`/api/v1/admin/protection-settings`)

Super Admin realm (`/api/v1/admin/**`), covered by that realm's existing broad
`.anyRequest().authenticated()` (research.md Decision 7 — no new matcher needed).

### `GET /api/v1/admin/protection-settings`

`200`: array of every named setting from data-model.md's table, each as
`{ "name": "string", "value": "string", "isDefault": boolean, "updatedAt": "instant | null", "updatedBy": "string | null" }`
— `isDefault: true` when no row exists yet and the documented default is being served (FR-029).

### `PUT /api/v1/admin/protection-settings/{name}`

Request: `{ "value": "string" }`. `200` with the updated setting. `400` for an unrecognized `name`
or a `value` that fails that setting's type/range validation (data-model.md). `404` never applies —
every valid `name` is always "gettable" (falls back to default), only writes to an unrecognized name
fail. Every successful `PUT` appends a `ProtectionSettingChangeLog` row (AUD-002).

### `GET /api/v1/admin/protection-settings/{name}/history`

`200`: array of `{ "previousValue": "string | null", "newValue": "string", "changedAt": "instant", "changedBy": "string" }`, newest first (AUD-002, AUD-004). `404` for an unrecognized `name`.

---

## Error body shape

All new error responses use this codebase's existing `{ "error": "CODE", "message": "..." }`
shape (matches every existing exception-handler convention already in this codebase — no new error
envelope introduced).
