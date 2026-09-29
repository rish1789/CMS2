# Contract: Patient Clinical Record Access

All four endpoints below are new, live in the patient JWT realm (`/api/v1/patients/**`), and
require a valid patient bearer token. None has a write method (GET only).

## `GET /api/v1/patients/bookings/{bookingId}/consultation-note`

Returns the consultation note for one of the caller's own bookings, or `null` if none was ever
written for that visit.

### Success — `200 OK`
```json
{ "id": "uuid", "bookingId": "uuid", "doctorProfileId": "uuid", "content": "string", "createdAt": "instant" }
```
or, if no note exists for this booking, `200 OK` with an **empty response body** (Spring's actual
behavior for a controller method returning `null`, confirmed during implementation — not a JSON
`null` literal). The frontend client treats an empty body the same as a `null` result.
(Reuses `ConsultationNoteResponse` unchanged — research.md Decision 4.)

### Errors
| Status | Condition |
|---|---|
| `401 Unauthorized` | No/invalid patient bearer token |
| `404 Not Found` | `bookingId` doesn't exist, or doesn't resolve to the caller's own patient account (research.md Decision 1 — both cases refused identically, no enumeration signal) |

---

## `GET /api/v1/patients/bookings/{bookingId}/prescriptions`

Returns every prescription tied to one of the caller's own bookings.

### Success — `200 OK`
```json
[
  {
    "id": "uuid",
    "bookingId": "uuid",
    "doctorProfileId": "uuid",
    "createdAt": "instant",
    "items": [
      { "id": "uuid", "medicationName": "string", "dosage": "string", "frequency": "string", "duration": "string", "instructions": "string" }
    ]
  }
]
```
An empty array `[]` is a valid, successful response — not an error (research.md Decision 5).
(Reuses `PrescriptionResponse`/`PrescriptionItemResponse` unchanged.)

### Errors
| Status | Condition |
|---|---|
| `401 Unauthorized` | No/invalid patient bearer token |
| `404 Not Found` | `bookingId` doesn't exist, or isn't the caller's own |

---

## `GET /api/v1/patients/bookings/{bookingId}/external-record-references`

Returns every external record reference tied to one of the caller's own bookings.

### Success — `200 OK`
```json
[
  {
    "id": "uuid",
    "bookingId": "uuid",
    "doctorProfileId": "uuid",
    "recordType": "string",
    "sourceProvider": "string",
    "recordDate": "date",
    "summary": "string",
    "createdAt": "instant"
  }
]
```
An empty array `[]` is a valid, successful response.
(Reuses `ExternalRecordReferenceResponse` unchanged.)

### Errors
| Status | Condition |
|---|---|
| `401 Unauthorized` | No/invalid patient bearer token |
| `404 Not Found` | `bookingId` doesn't exist, or isn't the caller's own |

---

## `GET /api/v1/patients/bookings/clinical-record-availability?bookingIds=id1,id2,...`

Bulk check: of the given booking ids, which have at least one clinical record (a consultation
note, a prescription, or an external record reference)? Backs the "which of my past visits have
something to view" indicator on the patient's own booking list (spec.md FR-004), in one request
instead of one-per-row (data-model.md — `ClinicalRecordAvailabilityService`).

### Request
Query param `bookingIds`: a comma-separated list of booking ids, all expected to be a subset of
what `GET /api/v1/patients/bookings` already returned for this same caller.

### Success — `200 OK`
```json
{ "bookingIdsWithRecords": ["uuid", "uuid"] }
```
Only ids that (a) are in the requested set, (b) resolve to the caller's own patient account, and
(c) have at least one record, ever appear — an id belonging to another patient is silently
excluded, never separately flagged (data-model.md).

### Errors
| Status | Condition |
|---|---|
| `401 Unauthorized` | No/invalid patient bearer token |
| `400 Bad Request` | `bookingIds` missing or empty |

## Contract Invariants (traced to spec)

- FR-001/FR-002/FR-003: each of the three per-type endpoints returns exactly the records that
  exist for a booking the caller owns — nothing more, nothing less.
- FR-004: the availability endpoint is the sole mechanism for "which visits have records," so a
  patient never has to open every visit individually to find out.
- FR-005: every endpoint's ownership check happens server-side, in the repository query itself
  (via `patient.patientAccount.id`), never only in how the frontend chooses to link to a booking —
  a manipulated `bookingId` in the URL is refused the same way an honest one that isn't the
  caller's would be.
- FR-006: no endpoint above has a corresponding POST/PUT/PATCH/DELETE — this contract is
  exhaustively read-only.
- FR-007/FR-008: none of these endpoints exist in the staff or super-admin realm, and nothing about
  the existing staff-side `POST/GET /api/v1/clinics/{clinicId}/bookings/{bookingId}/...` endpoints
  changes — this contract is purely additive in a different JWT realm.
- FR-009: a purged record is a deleted row (research.md Decision 2) — it is absent from these
  responses with no special-case handling, the same way it's absent from the staff-side response.
- FR-010: no endpoint above returns a file, a downloadable attachment, or sets any
  `Content-Disposition` — every response is inline JSON, matching the existing entities (which, per
  the investigation behind this spec, have no file/attachment field to begin with).
