# Quickstart: Fee Resolution & Locking at Booking Time

See [data-model.md](./data-model.md) and [contracts/fee-resolution.md](./contracts/fee-resolution.md).

## Prerequisites

- A Doctor onboarded (004/005/007), with a staff bearer token for the Doctor and for a ClinicAdmin at a clinic they're actively staffed at (013's pattern).

## Scenario 1 — Override wins

1. `POST .../appointment-types` with `{"name": "Follow-up", "feeOverride": 300}`. **Expect**: `201`.
2. `PUT .../default-fee` with `{"amount": 500}`. **Expect**: `200`.
3. Call `FeeResolutionService.resolve(doctorProfileId, appointmentTypeId)` directly. **Expect**: `300` (the override), not `500`.

## Scenario 2 — Falls through to default fee

1. `POST .../appointment-types` with `{"name": "New Patient"}` (no override). **Expect**: `201`.
2. `PUT .../default-fee` with `{"amount": 500}`.
3. `resolve(doctorProfileId, thatAppointmentTypeId)`. **Expect**: `500`.

## Scenario 3 — Hard block

1. `POST .../appointment-types` with `{"name": "Consultation"}` (no override), and no default fee ever set.
2. `resolve(doctorProfileId, thatAppointmentTypeId)`. **Expect**: throws `NoFeeConfiguredException` — no fee returned.

## Scenario 4 — Wrong doctor rejected

1. Create an Appointment Type for Doctor A. `resolve(doctorProfileB.id, thatAppointmentTypeId)`. **Expect**: throws `AppointmentTypeNotFoundException`.

## Scenario 5 — Configuration authorization

1. As the Doctor's own token: `POST .../appointment-types`. **Expect**: `201`.
2. As a ClinicAdmin at a clinic the Doctor is actively staffed at: same request. **Expect**: `201`.
3. As an unrelated staff member: same request. **Expect**: `403 FORBIDDEN`.
