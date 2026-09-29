# Quickstart: Staff Login (Password or Staff Code)

See [contracts/staff-login.md](./contracts/staff-login.md).

## Prerequisites

- A staff Account (001 ClinicAdmin, or a 004 Doctor/Operations hire) with a known email, staff code, and password.

## Scenario 1 — Login by staff code

1. `POST /api/v1/staff/login` with `identifier = <staffCode>`, correct password. **Expect**: `200`, same `accountId`/`email` as an email-based login for that Account would return.

## Scenario 2 — Wrong password with valid staff code

1. Same as above with an incorrect password. **Expect**: `401`, same shape as an unknown-email failure.

## Scenario 3 — Unknown staff code

1. `identifier` set to a staff code that doesn't exist. **Expect**: `401`, same shape as Scenario 2.
