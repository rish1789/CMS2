# Contract: Fee Resolution & Locking at Booking Time

## `FeeResolutionService.resolve(UUID doctorProfileId, UUID appointmentTypeId) -> BigDecimal` (service interface, not a REST endpoint)

Per Constitution Principle III's explicit allowance: no HTTP endpoint exists yet, since this feature's only eventual caller (016/017/018) is unbuilt. `com.cms.booking.FeeResolutionService`, `@Transactional(readOnly = true)`.

### Exceptions

| Exception | Condition |
|---|---|
| `AppointmentTypeNotFoundException` | No `AppointmentType` with `appointmentTypeId`, or it belongs to a different doctor than `doctorProfileId` |
| `NoFeeConfiguredException` | The `AppointmentType` has no override and the Doctor has no default fee |

### Invariants

- An override always wins when present, regardless of a default fee also being present (FR-001, SC-001).
- No override + a default fee always returns exactly the default fee (FR-002, SC-002).
- No override + no default fee always throws — never a fabricated or zero amount (FR-003, SC-003).

---

## REST endpoints (`com.cms.booking.BookingController`, `/api/v1/doctors/**`)

All three require a valid staff bearer token (own `BookingSecurityConfig` chain, `@Order(6)`). A missing/invalid token returns `401`.

### `POST /api/v1/doctors/{doctorProfileId}/appointment-types`

Request: `{ "name": "string, required", "feeOverride": "number, optional" }`
Success: `201`, `{ "id": "uuid", "doctorProfileId": "uuid", "name": "string", "feeOverride": number | null }`

### `GET /api/v1/doctors/{doctorProfileId}/appointment-types`

Success: `200`, array of the same shape as `POST`'s response.

### `PUT /api/v1/doctors/{doctorProfileId}/default-fee`

Request: `{ "amount": "number, required" }`
Success: `200`, `{ "doctorProfileId": "uuid", "amount": number }`

### Error Responses (all three endpoints)

| Status | Condition | Body `error` |
|---|---|---|
| `401 Unauthorized` | Missing/invalid staff bearer token | — |
| `403 Forbidden` | Caller is neither the named Doctor nor an active ClinicAdmin at any clinic that Doctor is actively staffed at | `FORBIDDEN` |
| `404 Not Found` | No `DoctorProfile` with `doctorProfileId` | `DOCTOR_PROFILE_NOT_FOUND` |

## Contract Invariants (traced to spec)

- A caller who is neither the named Doctor nor an active ClinicAdmin at any clinic that Doctor works at is always `403` on all three endpoints (FR-006, SC-004).
- The Doctor themselves always succeeds on all three endpoints (FR-005).
- No endpoint or service method here ever creates, modifies, or references a Booking/payment-status row (FR-009, SC-005).
