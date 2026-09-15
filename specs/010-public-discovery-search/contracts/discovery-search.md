# Contract: Public Discovery Search

No auth of any kind — no header is read or required. Scoped under `/api/v1/discovery/**` via its own `SecurityFilterChain` (`DiscoverySecurityConfig`, `@Order(5)`), which `permitAll()`s every request on that path.

## `GET /api/v1/discovery/search`

### Query Parameters

| Param | Required | Description |
|---|---|---|
| `q` | No | Free-text term matched (case-insensitive, substring) against doctor specialization, doctor name, clinic name, and clinic address. Absent, empty, or whitespace-only is treated as "no filter" — the full current eligible set is returned. |

### Success Response — `200 OK`

Always `200`, even when nothing matches (empty array, not an error):

```json
[
  {
    "doctorProfileId": "uuid",
    "doctorName": "string",
    "specialization": "string",
    "clinicId": "uuid",
    "clinicName": "string",
    "clinicAddress": "string"
  }
]
```

### Error Responses

None specific to this endpoint — no auth to fail, no path/body parameters to validate beyond an optional string. Malformed requests (e.g. unsupported HTTP method) fall through to the framework's standard error handling, unchanged from every other controller in this codebase.

## Contract Invariants (traced to spec)

- Every row returned satisfies all of: clinic verified, doctor license verified, doctor visible, doctor has an active Doctor-role Role Assignment at that specific returned clinic (FR-002, FR-003, SC-001).
- No row is ever returned for a clinic/doctor pairing that fails any one of those conditions, regardless of `q` (US1 AC1–AC3, AC5).
- A request with no `Authorization` header (or any other credential) succeeds identically to one with credentials attached — this endpoint never inspects them (FR-001, SC-003).
- The result set reflects the *current* verification/visibility state on every call — no caching layer sits in front of the eligibility conditions (FR-004, SC-002).
- `q` matching is case-insensitive substring against exactly four fields (specialization, doctor name, clinic name, clinic address) — never a fielded/structured query, never fuzzy/ranked (FR-005, FR-007).
- The response never contains license numbers, experience years, contact details, account credentials, or any clinical/booking data (FR-006, SC-005).
