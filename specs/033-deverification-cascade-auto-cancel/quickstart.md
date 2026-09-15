# Quickstart: De-Verification Cascade (Auto-Cancel Future Bookings)

See [data-model.md](./data-model.md) and [contracts/doctor-revoke.md](./contracts/doctor-revoke.md).

## Prerequisites

- A verified Clinic with staffed doctors, both Fixed-Time and Queue-mode Sessions with active
  Bookings, and Super Admin Basic Auth credentials.

## Scenario 1 — Un-verifying a clinic cancels every future booking (US1)

1. A verified Clinic has 3 active Fixed-Time bookings (one with a matching `WAITING` waitlist
   entry) and 1 active Queue-mode booking, plus 1 already-`COMPLETED` Fixed-Time booking.
2. As Super Admin, `POST /api/v1/admin/clinics/{clinicId}/unverify`.
3. **Expect**: all 4 active bookings are `CANCELLED`; the one with a waiting entry produces a
   waitlist offer (028); the already-`COMPLETED` booking is untouched.
4. As Super Admin, `POST /api/v1/admin/clinics/{clinicId}/verify` (re-verify). **Expect**: the 4
   cascade-cancelled bookings remain `CANCELLED` — they are not restored.

## Scenario 2 — Explicitly revoking a doctor's license cancels their future bookings (US2)

1. A verified Doctor has active bookings at two different clinics.
2. As Super Admin, `POST /api/v1/admin/doctors/{doctorProfileId}/revoke`. **Expect**: `200`,
   `licenseVerified: false`; every one of that doctor's active bookings, at both clinics, is now
   `CANCELLED`.

## Scenario 3 — 006's automatic reset never cascades

1. A verified Doctor edits their own license number (triggering 006's automatic reset) while
   holding active future bookings.
2. **Expect**: `licenseVerified` becomes `false`, but none of those bookings are cancelled.

## Scenario 4 — Idempotent re-trigger

1. Un-verify an already-unverified Clinic (or revoke an already-unverified Doctor).
2. **Expect**: `200`, no error, and no second cascade run (no double-cancellation, no duplicate
   notification events).
