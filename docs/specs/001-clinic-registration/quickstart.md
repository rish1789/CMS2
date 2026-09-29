# Quickstart: Clinic Registration

Validates that the feature works end-to-end. See [data-model.md](./data-model.md) for entity details and [contracts/register-clinic.md](./contracts/register-clinic.md) for the full request/response contract.

## Prerequisites

- Backend running locally with a PostgreSQL instance available (e.g. via Testcontainers for automated runs, or a local Postgres for manual runs) and Flyway migrations applied.
- No authentication needed — this is a public endpoint.

## Scenario 1 — Happy path

1. `POST /api/v1/clinics/register` with a valid clinic + admin payload (see contract).
2. **Expect**: `201 Created`, with `verified: false` and a non-empty `admin.staffCode`.
3. Query the database directly: confirm one `Clinic` row, one `Account` row, and one `Role Assignment` row (`role = ClinicAdmin`, `active = true`) all exist.
4. Attempt to log in as the new admin two ways: (a) `admin.email` + the submitted password, (b) `admin.staffCode` + the submitted password. **Expect**: both succeed and resolve to the same Account (exercises 003's contract, not built by this feature, but the Account this feature creates must already satisfy it).
5. Run a public discovery search (once 035 exists) for the new clinic. **Expect**: it does not appear (unverified).

## Scenario 2 — Atomicity on failure

1. Force a failure partway through creation (e.g. a test-only fault injected after Clinic insert but before Account insert, or a request payload that passes request validation but violates a DB constraint unexpectedly).
2. **Expect**: `500 Internal Server Error` (or the relevant error), and zero rows left behind — no Clinic, no Account, no Role Assignment. Confirms FR-003.

## Scenario 3 — Duplicate email rejected

1. Register successfully once with `admin.email = "owner@example.com"`.
2. Attempt a second registration (different clinic, same `admin.email`).
3. **Expect**: `409 Conflict`, `error: "EMAIL_ALREADY_IN_USE"`. Confirms FR-012. No new Clinic, Account, or Role Assignment row is created by the second attempt.

## Scenario 4 — Password policy enforcement

1. Attempt registration with `admin.password = "short"` (fails length, uppercase, digit, special-char rules).
2. **Expect**: `400 Bad Request`, `error: "INVALID_PASSWORD"`, with `failedRules` listing every rule that failed (not just the first one found). Confirms FR-009.

## Scenario 5 — Mobile number validation

1. Attempt registration with `clinic.contactMobile = "12345"` (invalid format).
2. **Expect**: `400 Bad Request`, `error: "INVALID_MOBILE_NUMBER"`, `field: "clinic.contactMobile"`. Confirms FR-010.
3. Repeat the happy path (Scenario 1) with `contactMobile` and `admin.mobile` both omitted entirely.
4. **Expect**: `201 Created` — confirms the mobile number is genuinely optional (FR-011).

## Scenario 6 — No excluded fields exist

1. Inspect the request/response schema (contract) and the rendered registration form.
2. **Expect**: no Grievance Officer field, no billing/payment field, no file-upload field anywhere. Confirms FR-006, FR-007, FR-008.
