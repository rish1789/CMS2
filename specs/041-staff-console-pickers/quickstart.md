# Quickstart: Clinic Staff Console — Browse & Pick Instead of Type-an-ID

See [data-model.md](./data-model.md) and [contracts/staff-console-pickers.md](./contracts/staff-console-pickers.md).

## Prerequisites

- Backend running with PostgreSQL and Flyway migrations applied.
- At least one staff Account with an active role at 1+ clinics (004), at least one clinic with a generated session within the next 14 days (011/012/013), at least one booked slot (016/017/018) with a linked Patient (019).

## Scenario 1 — Pick a clinic instead of typing its ID (US1)

1. `GET /api/v1/clinics/mine` with a staff bearer token. **Expect**: `200`, every clinic that Account has an active role at, by name.
2. In the browser: log in as that staff member. **Expect**: the dashboard shows those clinics by name; clicking one enters `/staff/clinics/{clinicId}`, same destination as today's manual entry.
3. Log in as a staff member with zero active roles anywhere. **Expect**: a clear "no clinics" message, not a blank screen.

## Scenario 2 — Browse a day sheet to find and act on a booking (US2)

1. `GET /api/v1/clinics/{clinicId}/sessions` (no `from`/`to`, defaults apply). **Expect**: `200`, sessions within the next 14 days, each with doctor name/date/mode.
2. `GET /api/v1/clinics/{clinicId}/sessions/{sessionId}/day-sheet` for one Fixed-Time session with a booked slot. **Expect**: `200`, that slot's `booking.patientName` populated; an unbooked slot's `booking` is `null`.
3. For a session with a buffer slot: **Expect** that slot's `isBuffer: true`, and in the browser, that row shows "Reserved capacity" with no Book button.
4. In the browser: click a booked slot's "Cancel booking" action. **Expect**: navigates to `/staff/clinics/{clinicId}/bookings/{bookingId}/cancel` — the same, unchanged destination route — with the booking ID already filled in.

## Scenario 3 — Search for a patient instead of typing their ID (US3)

1. `GET /api/v1/clinics/{clinicId}/patients/search?q=<partial name>`. **Expect**: `200`, matching patients by name.
2. `GET .../patients/search?q=<phone fragment>`. **Expect**: `200`, matches by phone too.
3. `GET .../patients/search?q=zzznomatch`. **Expect**: `200`, empty `patients` array — not an error.
4. In the browser: select a search result on the "Anonymize a patient" tool. **Expect**: navigates to `/staff/clinics/{clinicId}/patients/{patientId}/anonymize` with the ID already filled in.

## Scenario 4 — Pick a doctor or staff member instead of typing their ID (US4)

1. `GET /api/v1/clinics/{clinicId}/doctors`. **Expect**: `200`, doctors staffed at that clinic by name and specialization.
2. `GET /api/v1/clinics/{clinicId}/staff`. **Expect**: `200`, active staff at that clinic by name and role.
3. In the browser: pick a doctor on "Define a schedule". **Expect**: navigates to `/staff/clinics/{clinicId}/doctors/{doctorProfileId}/schedule` with the ID already filled in.

## Scenario 5 — No new authorization concept (FR-010)

1. Call any clinic-scoped endpoint above with a valid staff JWT for an Account with **no** active role at that `clinicId`. **Expect**: `403`, same as any existing action endpoint would return for the same caller/clinic pair.
2. Call any endpoint above with no `Authorization` header. **Expect**: `401`.
