# Contract: Today's Session Stats

## `GET /api/v1/clinics/{clinicId}/sessions/today-stats`

Returns today's completed and no-show slot counts for this clinic, computed from `Slot.status` across all of today's sessions. Reuses the exact "any active role at this clinic" authorization gate as its sibling `GET /api/v1/clinics/{clinicId}/sessions` (`ClinicSessionListController`) — no doctor-self-scoping (unlike the sibling list endpoint, this is an aggregate count, not a per-session listing, so there is no per-doctor row to scope).

### Success Response — `200 OK`

```json
{ "completedCount": 3, "noShowCount": 1 }
```

Both fields are always present, defaulting to `0` when no `Slot` has that status today (e.g. a brand-new clinic, or before any of today's sessions have started).

### Error Responses

| Status | Condition |
|---|---|
| `401 Unauthorized` | Missing/invalid staff bearer token |
| `403 Forbidden` (`NOT_STAFFED_AT_CLINIC` / `FORBIDDEN`) | Caller has no active `RoleAssignment` at `clinicId` — identical gate and error shape to the sibling `GET /api/v1/clinics/{clinicId}/sessions` |

## Contract Invariants (traced to spec)

- `completedCount`/`noShowCount` are always computed from real `Slot.status` values for today (`Slot.session.sessionDate = today`) at this clinic — never hardcoded, never estimated (FR-004, SC-003).
- A caller with no active role at this clinic gets `403`, identical to the sibling session-list endpoint — no new authorization mechanism (FR-009).
- The date used is always the server's "today," matching `ClinicToolsDashboard.tsx`'s own existing `todayIsoDate()` computation used for its other tiles — no client-supplied date parameter (this endpoint is dashboard-specific, unlike the sibling list endpoint's date-range params).
