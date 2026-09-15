# Quickstart: Last Active ClinicAdmin Protection

See [data-model.md](./data-model.md) and [contracts/staff-deactivation.md](./contracts/staff-deactivation.md).

## Prerequisites

- A clinic (001) with its founding ClinicAdmin, plus at least one Operations or Doctor hire (004) to deactivate in Scenario 1.

## Scenario 1 — Deactivate a Doctor/Operations staff member

1. Log in as the ClinicAdmin (`POST /api/v1/staff/login`).
2. `POST /api/v1/clinics/{clinicId}/staff/{operationsAccountId}/deactivate`. **Expect**: `200`, `active: false`.

## Scenario 2 — Last active ClinicAdmin blocked

1. `POST /api/v1/clinics/{clinicId}/staff/{clinicAdminAccountId}/deactivate` (the founding, only ClinicAdmin). **Expect**: `409 LAST_ACTIVE_CLINIC_ADMIN`. Role Assignment remains active.

## Scenario 3 — Idempotency

1. Repeat Scenario 1's deactivation call. **Expect**: `200`, `active: false`, unchanged — no error.

## Scenario 4 — Authorization

1. Call the endpoint with no token. **Expect**: `401`.
2. Call it with a valid Doctor/Operations (non-ClinicAdmin) token. **Expect**: `403`.
3. Call it targeting a different clinic's staff than the caller's own. **Expect**: `403`.
