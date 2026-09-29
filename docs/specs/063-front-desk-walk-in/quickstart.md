# Quickstart: Front-Desk Walk-In Registration (063)

Live verification against the dev stack (`backend` + `frontend` in `.claude/launch.json`), using throwaway data only. Clean it up afterwards the same way as for 062.

## Setup

A throwaway clinic with one Doctor who has:
- a default fee and one appointment type;
- a Fixed-Time schedule covering now (5-minute slots, starting a little in the past);
- a Queue schedule today.

Also: two patient accounts, and one booked Fixed-Time appointment.

## Scenario 1 — Register a new walk-in into a Fixed-Time session (US1)

From the Walk-in screen:
1. Choose **New patient** → name, phone, email.
2. Visit reason **Pain**.
3. Pick the Fixed-Time session.
4. Choose an appointment type.
5. Register.

Expect:
- The confirmation shows **W1**, position 1, and the locked fee.
- The Day Sheet shows W1 in the **Walk-in line** with a walk-in badge and "Pain".
- The booked appointment is unchanged; timed slot counts are unchanged.
- A staff inbox walk-in notice was created.

## Scenario 2 — "Other" reason and the duplicate warning (US1, FR-004/FR-017)

1. Register an existing patient with **Other** and no text → blocked.
2. Add text → register → **W2**.
3. Register the same patient again into the same session → a duplicate warning appears; confirming registers **W3**.

## Scenario 3 — Phone match (FR-003)

Enter a new patient whose phone matches an existing patient → the match is offered; picking it uses the existing record.

## Scenario 4 — Doctor step data (FR-006/FR-007)

The session list shows each of today's sessions with:
- live status;
- booked count;
- walk-ins waiting (2–3);
- Doctor free/busy.

A doctor whose setup is incomplete shows as unavailable, with the reason.

## Scenario 5 — Send in and complete (US2)

1. With nobody in with the doctor → **Doctor free now**.
2. **Send in** W1 → W1 is In with doctor, `appearedAt` is set, the hint turns **Doctor busy**, and W2 is now first.
3. **Complete** → `completedAt` is set; the hint returns to free.

## Scenario 6 — Remove a walk-in (FR-015)

**Remove** W2 → the booking is cancelled, it leaves the line, and it is not a no-show. The untimed slot is **not** offered in patient slot listings, and no waitlist offer is created.

## Scenario 7 — Queue session (US1/US3)

Register a walk-in into the Queue session → it gets the next token in the same line, is marked as a walk-in with its reason on the Day Sheet, and creates the inbox notice. The confirmation shows the token (no position).

## Scenario 8 — Day Sheet shortcut (US4)

From each session's Day Sheet, the walk-in button opens the Walk-in screen with that session pre-selected. The old `/sessions/:id/walk-in` URL redirects there.

## Scenario 9 — Safety sweeps

Wait past the no-show grace period with a walk-in still waiting → it is **not** marked no-show. With a walk-in In with doctor past the session end → it is **not** auto-completed.

## Automated coverage

- Backend: `gradle test --tests "*.unit.*" --tests "*.contract.*"`; integration classes are Docker-gated.
- Frontend: `npx vitest run`, `npx tsc -b`, `npm run lint`.
