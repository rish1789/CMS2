# Contract: Staff Login (extended)

## `POST /api/v1/staff/login`

**Changed from 004**: request field `email` → `identifier` (accepts either an email or a staff code).

### Request
```json
{ "identifier": "string, required (email or staff code)", "password": "string, required" }
```

### Success — `200 OK`
```json
{ "token": "string (JWT, STAFF audience)", "accountId": "uuid", "email": "string" }
```
Identical shape and content regardless of which identifier type was used to log in (FR-002) — the response always reports the Account's actual `email`, even if `identifier` was a staff code.

### Errors
| Status | Condition |
|---|---|
| `401 Unauthorized` | Unknown identifier (email or staff code) OR wrong password — identical response shape for all three cases (FR-004) |

## Contract Invariants (traced to spec)

- A staff-code login and an email login for the same Account produce byte-identical response bodies aside from the token itself (FR-002).
- No response ever indicates whether a failed `identifier` was an unrecognized email, an unrecognized staff code, or whether the identifier matched but the password was wrong (FR-004).
