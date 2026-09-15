# Quickstart: Self-Service Waitlist Claim

See [data-model.md](./data-model.md) and [contracts/waitlist-claim.md](./contracts/waitlist-claim.md).

## Prerequisites

- A verified Clinic with a staffed Doctor, a confirmed Fixed-Time Booking to cancel via 025's
  individual cancellation (triggering 028's matching), a waiting Patient Account with an
  `OFFERED` waitlist entry as a result, and a patient token for that account.

## Scenario 1 — Claiming an offer (US1)

1. Cancel a confirmed Booking for a doctor with a waiting, matching entry (per 028's own flow) —
   the entry transitions to `OFFERED`.
2. As that entry's patient, `POST /api/v1/patients/waitlist-entries/{entryId}/claim` with
   `{"appointmentTypeId": ..., "patientName": "..."}`. **Expect**: `201`, a confirmed Booking for
   the offered Slot, fee resolved and locked at this moment.

## Scenario 2 — Claim rejected: not the owner, or window lapsed

1. As a different patient, attempt the same claim. **Expect**: `404 WAITLIST_ENTRY_NOT_FOUND`.
2. Wait past the 30-minute window (or run the expiry sweep, Scenario 4) and attempt to claim.
   **Expect**: `409 WAITLIST_OFFER_NOT_CLAIMABLE`.

## Scenario 3 — Declining re-offers the next entry (US2)

1. With two eligible waiting entries for the same doctor, cancel a Booking so the
   longest-waiting one is `OFFERED`.
2. As that patient, `POST /api/v1/patients/waitlist-entries/{entryId}/decline`. **Expect**: `200`,
   `status: "EXPIRED"`.
3. **Expect**: the second entry is now `OFFERED` the same Slot, with a fresh 30-minute window.

## Scenario 4 — Expiry sweep re-offers the next entry (US3)

1. With an `OFFERED` entry whose window has already lapsed (backdated in test fixtures) and
   another eligible waiting entry present, trigger the sweep service directly (test-only —
   production fires it via `@Scheduled` every minute). **Expect**: the lapsed entry is now
   `EXPIRED`; the next eligible entry is now `OFFERED` the same Slot with a fresh window.
2. Repeat with no further eligible entries. **Expect**: the last entry expires, no further offer
   is made, and the Slot remains available for regular booking.

## Scenario 5 — Claim loses a race to an ordinary booking

1. With an `OFFERED` entry, book its offered Slot directly through the ordinary booking flow
   (016/017) before the offer is claimed.
2. As the offered entry's patient, attempt to claim it. **Expect**: `409 SLOT_ALREADY_BOOKED`; the
   entry is now `EXPIRED`.
3. **Expect**: no other entry is offered this same Slot, even if another eligible entry exists —
   the Slot itself is already taken (research.md R10: re-matching only ever offers a still-`OPEN`
   Slot), so this Slot's waitlist is simply over.

## Scenario 6 — Frontend

1. As a patient with an `OFFERED` entry, see the offer and claim or decline it
   (`frontend/src/features/waitlist/ClaimOfferCard.tsx`).
