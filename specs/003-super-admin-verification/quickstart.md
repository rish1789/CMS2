# Quickstart: Super Admin Clinic Verification

See [data-model.md](./data-model.md) and [contracts/clinic-verification.md](./contracts/clinic-verification.md).

## Prerequisites

- Backend running with PostgreSQL and Flyway migrations applied, plus `admin.super-admin.username`/`password` configured (e.g. via environment variables).
- A clinic already registered (001) to act on.

## Scenario 1 — Verify a pending clinic

1. `GET /api/v1/admin/clinics/pending` with Super Admin Basic Auth. **Expect**: the newly registered clinic appears.
2. `POST /api/v1/admin/clinics/{id}/verify`. **Expect**: `200`, `verified: true`.
3. Repeat `GET /api/v1/admin/clinics/pending`. **Expect**: the clinic no longer appears.

## Scenario 2 — Un-verify and event publication

1. `POST /api/v1/admin/clinics/{id}/unverify` on a verified clinic. **Expect**: `200`, `verified: false`, and `ClinicDeVerifiedEvent` published exactly once (assert via a test listener).
2. Repeat the same `unverify` call. **Expect**: `200`, `verified: false` again, but the event is NOT published a second time (FR-007).

## Scenario 3 — Unauthorized access rejected

1. Call any `/api/v1/admin/**` endpoint with no credentials. **Expect**: `401`.
2. Call the same endpoint with a valid 001 ClinicAdmin's email+password as Basic Auth credentials. **Expect**: `401` — a staff Account is never accepted here.

## Scenario 4 — Idempotent verify

1. `POST /api/v1/admin/clinics/{id}/verify` on an already-verified clinic. **Expect**: `200`, `verified: true`, no error.
