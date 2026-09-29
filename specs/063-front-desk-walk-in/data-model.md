# Data Model: Front-Desk Walk-In Registration (063)

One forward-only migration: `V39__front_desk_walk_in.sql`. All new columns are nullable, so existing rows stay valid.

## Changed tables

| Table | New column | Type | Notes |
|---|---|---|---|
| `booking` | `visit_reason` | `VARCHAR(40)` | `VisitReason` enum name. Set for every front-desk walk-in (FR-004); null for booked visits and older rows. |
| `booking` | `visit_reason_detail` | `VARCHAR(200)` | Free text, required when `visit_reason = 'OTHER'`. **Check constraint** `ck_booking_visit_reason_other_detail`: `visit_reason <> 'OTHER' OR (visit_reason_detail IS NOT NULL AND length(trim(visit_reason_detail)) > 0)`. |
| `patient` | `email` | `VARCHAR(254)` | Optional contact email on the clinic patient record (not a login). |
| `slot` | `appeared_at` | `TIMESTAMPTZ` | Set when the patient is sent in (existing Appeared action). |
| `slot` | `completed_at` | `TIMESTAMPTZ` | Set when the visit is completed (manual or automatic). |

## Enum: VisitReason

`FEVER_COLD_COUGH` (Fever / Cold & cough), `PAIN`, `FOLLOW_UP` (Follow-up visit), `TEST_REPORT_REVIEW` (Test / report review), `PRESCRIPTION_REFILL`, `INJURY`, `GENERAL_CHECKUP` (General check-up), `OTHER`

## Walk-in line (no new table)

A Fixed-Time walk-in is:
- **Slot**: the Fixed-Time session's own slot, with `start_time = NULL`, `end_time = NULL`, `token_number = n`, status `BOOKED`;
- **Booking**: `source = WALK_IN`, with the visit reason.

**Ordering and numbering**: tokens come from the existing per-session counter and unique index `(session_id, token_number)`. A Fixed-Time session's timed slots have a null token, so walk-in numbering starts at 1 (W1).

**Waiting** = the slot is `BOOKED` and the booking is `ACTIVE`.

## State transitions (walk-in slot)

```
(registered) BOOKED --Send in (Appeared)--> APPEARED --Complete--> COMPLETED
     |  appeared_at stamped                 completed_at stamped
     +--Remove (staff cancel)--> booking CANCELLED, slot OPEN (never re-offered: untimed slots are
                                  excluded from every bookable-slot query; no waitlist bump)
```

The automatic no-show and auto-completion sweeps never touch untimed slots.

## Invariants (tested)

- An untimed slot in a Fixed-Time session is never listed, booked by slot id, or offered to the waitlist.
- Registering a walk-in changes no timed slot and no booked appointment (SC-003).
- A walk-in's visit reason is always present; "Other" always has detail (enforced by the service and the DB constraint).
