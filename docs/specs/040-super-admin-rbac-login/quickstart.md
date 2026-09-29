# Quickstart: Super Admin RBAC Login & Console Access

See [data-model.md](./data-model.md) and [contracts/clinic-portal-login.md](./contracts/clinic-portal-login.md).

## Prerequisites

- Backend running with PostgreSQL and Flyway migrations applied, `admin.super-admin.username`/`password` configured, and a new `admin.super-admin.jwt.secret` configured (or its dev-only default left in place).
- At least one staff Account already onboarded (004) to act as the "staff" side of the shared login.

## Scenario 1 — Super Admin logs in via the Clinic Portal and reaches the console (US1)

1. `POST /api/v1/staff/login` with `{ "identifier": "<configured super-admin username>", "password": "<configured super-admin password>" }`. **Expect**: `200`, `role: "SUPER_ADMIN"`, `accountId: null`, a JWT `token`.
2. In the browser: submit the same credentials on the Clinic Portal login screen (`/staff/login`). **Expect**: redirected to `/super-admin-console`, console content visible immediately.
3. Call `GET /api/v1/admin/clinics?verified=false` with `Authorization: Bearer <token>` from step 1. **Expect**: `200`, the endpoint's existing pending-clinics response.

## Scenario 2 — Staff and patient logins are unaffected (US2)

1. `POST /api/v1/staff/login` with a real staff Account's email + password. **Expect**: `200`, `role: "STAFF"`, `accountId` populated — identical to pre-feature behavior aside from the added `role` field.
2. In the browser: log in as that staff Account on `/staff/login`. **Expect**: redirected to `/staff` (the existing staff dashboard), never `/super-admin-console`.
3. Log in as a patient on `/patient/login`. **Expect**: redirected to `/patient`, unchanged; no interaction with the Clinic Portal at all.

## Scenario 3 — Console and endpoints reject anyone who isn't Super Admin (US3)

1. In the browser, while logged out: navigate directly to `/super-admin-console`. **Expect**: redirected to `/staff/login`, no console content rendered.
2. Log in as staff, then navigate to `/super-admin-console`. **Expect**: redirected to `/staff/login` again — a valid staff session does not grant console access.
3. Call `GET /api/v1/admin/clinics?verified=false` with no `Authorization` header. **Expect**: `401 {"error":"UNAUTHORIZED"}`.
4. Repeat with `Authorization: Bearer <staff token from Scenario 2>`. **Expect**: `401`, same body — a valid staff JWT is rejected identically to no credential at all.
5. Repeat with `Authorization: Basic <base64 of the correct super-admin username:password>`. **Expect**: `401` — Basic Auth is no longer accepted at all, even with correct credentials (FR-012).

## Scenario 4 — Generic failure on unrecognized credentials (FR-004)

1. `POST /api/v1/staff/login` with `{ "identifier": "not-a-real-identifier", "password": "wrong" }`. **Expect**: `401`, `{"error":"INVALID_CREDENTIALS"}`.
2. `POST /api/v1/staff/login` with the correct Super Admin username but a wrong password. **Expect**: the same `401 INVALID_CREDENTIALS` body as step 1 — no indication the username was recognized.
