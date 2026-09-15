# Quickstart: Unified Real-Time Inbox

See [data-model.md](./data-model.md) and [contracts/inbox.md](./contracts/inbox.md).

## Prerequisites

- Two Operations/ClinicAdmin staff tokens for the same clinic (to observe cross-viewer real-time
  behavior), and a Session/Slot/Doctor/Waitlist setup capable of exercising 020 (walk-in), 029
  (waitlist offer), and 008 (de-verification cascade).

## Scenario 1 — Walk-in insertion creates a real-time item (US1)

1. Staff A opens `GET .../inbox/stream` (or the frontend Inbox page) for the clinic.
2. Staff B performs a walk-in insertion (`POST .../sessions/{sessionId}/walk-in`, per 025's own
   quickstart).
3. **Expect**: within 5 seconds, Staff A's stream emits a new `WALK_IN` item referencing the new
   Booking, with no manual refresh (SC-001).

## Scenario 2 — Waitlist offer and de-verification cascade also surface (US1)

1. Trigger a waitlist offer (per 031/032's own quickstart — an individual cancellation matching a
   waiting entry) and, separately, a doctor license revoke that cascades bookings (per 033's own
   quickstart).
2. **Expect**: a `WAITLIST_OFFER` item and a `DEVERIFICATION_CASCADE` item each appear at the
   affected clinic(s), the latter summarizing the doctor and cancelled-booking count.

## Scenario 3 — Clinic scoping (US1)

1. Staff at Clinic A and Staff at Clinic B each open their own Inbox.
2. Trigger a walk-in at Clinic A only.
3. **Expect**: Clinic A's Inbox shows the new item; Clinic B's never does, in either the list or
   stream endpoint (SC-003).

## Scenario 4 — Claim prevents duplicate work (US2)

1. Two staff sessions (A, B) at the same clinic view the same unclaimed item.
2. A calls `POST .../inbox/{itemId}/claim`. **Expect**: `200`, item now `CLAIMED` by A; B's stream
   reflects this in real time.
3. B calls `POST .../inbox/{itemId}/claim` on the same item. **Expect**: `409 ALREADY_CLAIMED`.

## Scenario 5 — Concurrent claim (SC-002)

1. Fire two concurrent `claim` requests from different staff accounts against the same unclaimed
   item. **Expect**: exactly one `200`, the other `409 ALREADY_CLAIMED`.

## Scenario 6 — Release and reclaim (US2, Edge Cases)

1. A claims an item, then calls `POST .../inbox/{itemId}/release`. **Expect**: `200`, item back to
   `UNCLAIMED`.
2. B now claims the same item. **Expect**: `200`, succeeds (no longer blocked by A's prior claim).

## Scenario 7 — Resolve, claimant-only (US3)

1. A claims an item, then B (not the claimant) calls `POST .../inbox/{itemId}/resolve`. **Expect**:
   `409 NOT_CLAIMANT`.
2. A calls `resolve` on the same item. **Expect**: `200`, item `RESOLVED`; it no longer appears in
   `GET .../inbox`'s list for any staff at that clinic (FR-012).

## Scenario 8 — Waitlist-offer auto-resolve (US3, FR-013)

1. A `WAITLIST_OFFER` item exists, unclaimed. The patient claims (or declines, or lets expire) the
   underlying offer via 029's own patient-facing flow.
2. **Expect**: the Inbox Item transitions to `RESOLVED` automatically — no staff claim or resolve
   call required — and disappears from the list/stream for all staff at that clinic.

## Scenario 9 — Anonymization propagates to Inbox content (FR-016, Clarifications)

1. A `WALK_IN` Inbox Item exists referencing a Patient later anonymized via 033's endpoint.
2. Re-fetch `GET .../inbox` (before the item is resolved). **Expect**: the item's `summary.patientName`
   now reflects the anonymized (scrubbed) name — no separate purge step was needed for the Inbox
   Item itself.

## Scenario 10 — Authorization

1. Call any Inbox endpoint with a Doctor's own token (not Operations/ClinicAdmin) at that clinic.
   **Expect**: `403 FORBIDDEN`.

## Scenario 11 — Frontend

1. As Operations/ClinicAdmin in the UI, open the Inbox page (`frontend/src/features/inbox/InboxPage.tsx`).
   **Expect**: outstanding items list, live updates as a second browser session claims/resolves
   items, and a visible claimant name on claimed items.
