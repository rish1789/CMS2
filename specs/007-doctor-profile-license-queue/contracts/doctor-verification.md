# Contract: Super Admin Doctor License Verification

All endpoints under `/api/v1/admin/**` require HTTP Basic Auth with the configured Super Admin credentials (same chain as `contracts/clinic-verification.md` in 003-super-admin-verification). A missing/invalid credential returns `401 Unauthorized`. A valid staff Account credential (001/004) is not accepted here.

## `GET /api/v1/admin/doctors?verified={true|false}`

Lists Doctor Profiles matching the given `licenseVerified` state — `false` for the pending-verification tab, `true` for the verified tab. `verified` is required.

### Success Response — `200 OK`

```json
{
  "doctors": [
    {
      "doctorProfileId": "uuid",
      "accountId": "uuid",
      "specialization": "string",
      "licenseNumber": "string",
      "experienceYears": 0,
      "licenseVerified": false,
      "visible": true
    }
  ]
}
```

## `POST /api/v1/admin/doctors/{doctorProfileId}/verify`

Marks the Doctor Profile's license verified. Idempotent — succeeds identically whether it was `false` or already `true` (FR-007). There is no `unverify` endpoint in this feature — license de-verification, if ever needed, is out of scope here (unlike Clinic's two-way toggle in 003).

### Success Response — `200 OK`

```json
{ "doctorProfileId": "uuid", "licenseVerified": true }
```

### Error Responses

| Status | Condition |
|---|---|
| `401 Unauthorized` | Missing/invalid Super Admin credentials |
| `404 Not Found` | No Doctor Profile with that ID |

## Contract Invariants (traced to spec)

- Every endpoint under `/api/v1/admin/doctors/**` rejects a request with no credentials or with valid-but-non-Super-Admin (staff Account) credentials as `401` (FR-006).
- `verify` called twice in a row against the same Doctor Profile produces the identical response both times, with no duplicate side effects (FR-007).
- The pending-doctors list never includes a Doctor Profile with `licenseVerified = true` (FR-004).
