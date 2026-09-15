# Contract: Nightly Rolling Session Generation

## `POST /api/v1/admin/sessions/generate`

Behind the existing `SuperAdminSecurityConfig` chain (`/api/v1/admin/**`, HTTP Basic Auth against the configured Super Admin credentials). A missing/invalid credential returns `401 Unauthorized` (existing chain behavior, unchanged by this feature).

### Request

No body.

### Success Response — `200 OK`

```json
{
  "runDate": "2026-09-03",
  "sessionsCreated": 42
}
```

### Error Responses

| Status | Condition |
|---|---|
| `401 Unauthorized` | Missing/invalid Super Admin credentials (existing chain behavior) |

## Contract Invariants (traced to spec)

- A call with valid Super Admin credentials always runs the identical generation logic the nightly job runs, using the current date as the run date (FR-007, SC-004).
- A call without valid Super Admin credentials never executes any generation logic (FR-008, SC-004).
- Calling this endpoint twice in immediate succession returns `sessionsCreated: 0` on the second call (no new dates became applicable in between) — idempotent per FR-002.
