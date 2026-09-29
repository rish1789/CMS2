# Contract: Super Admin Clinic Verification

All endpoints under `/api/v1/admin/**` require HTTP Basic Auth with the configured Super Admin credentials. A missing/invalid credential returns `401 Unauthorized`. A valid staff Account credential (001) is not accepted here — this is a structurally separate check.

## `GET /api/v1/admin/clinics?verified={true|false}`

Lists clinics matching the given `verified` state — `false` for the pending-verification tab, `true` for the verified/un-verify tab. `verified` is required.

### Success Response — `200 OK`

```json
{
  "clinics": [
    { "clinicId": "uuid", "name": "string", "address": "string", "contactEmail": "string|null", "contactMobile": "string|null", "createdAt": "timestamp" }
  ]
}
```

## `POST /api/v1/admin/clinics/{clinicId}/verify`

Marks the clinic verified. Idempotent — succeeds identically whether the clinic was `false` or already `true`.

### Success Response — `200 OK`

```json
{ "clinicId": "uuid", "verified": true }
```

### Error Responses

| Status | Condition |
|---|---|
| `401 Unauthorized` | Missing/invalid Super Admin credentials |
| `404 Not Found` | No clinic with that ID |

## `POST /api/v1/admin/clinics/{clinicId}/unverify`

Marks the clinic unverified. Idempotent — succeeds identically whether the clinic was `true` or already `false`. Publishes `ClinicDeVerifiedEvent` **only** on an actual `true → false` transition, never on a repeated call against an already-`false` clinic (FR-007).

### Success Response — `200 OK`

```json
{ "clinicId": "uuid", "verified": false }
```

### Error Responses

| Status | Condition |
|---|---|
| `401 Unauthorized` | Missing/invalid Super Admin credentials |
| `404 Not Found` | No clinic with that ID |

## Contract Invariants (traced to spec)

- Every endpoint under `/api/v1/admin/**` rejects a request with no credentials or with valid-but-non-Super-Admin (staff Account) credentials as `401` (FR-004).
- `verify`/`unverify` called twice in a row with the same target state produce the identical response both times, and the event fires at most once across both calls (FR-007).
- The pending-clinics list never includes a clinic with `verified = true` (FR-001).
