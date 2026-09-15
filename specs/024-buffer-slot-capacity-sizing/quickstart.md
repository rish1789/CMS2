# Quickstart: Buffer Slot Capacity Sizing

Prerequisites: a running backend against Postgres (Testcontainers in tests, or a real
instance), a Fixed-Time Schedule for a doctor, and (for Scenarios 2+) some `Booking`s against
that doctor's past Slots with a mix of `NO_SHOW` and non-`NO_SHOW` statuses within the trailing
90 days.

Since this feature has no HTTP endpoint, verification is via direct service invocation (in
tests) or by inspecting generated Sessions' Slots (`isBuffer()`) after a real generation run.

## Scenario 1 — Fewer than 5 resolved bookings: flat default

1. A doctor with 0–4 Bookings (of any status) in the trailing 90 days.
2. Call `RiskBasedBufferSlotCalculator.calculateBufferSlotCount(session)` directly (or generate
   a session for them via `SessionGenerationService`).
3. **Expect**: exactly 1 buffer slot.

## Scenario 2 — 5+ resolved bookings, nonzero no-show rate

1. A doctor with 5+ Bookings in the trailing 90 days, some fraction `NO_SHOW`.
2. Call the calculator (or generate a session).
3. **Expect**: the buffer count reflects that rate (as a direct percentage of the session's
   slots), capped at 20% of slots and 3 absolute.

## Scenario 3 — 5+ resolved bookings, zero no-shows

1. A doctor with 5+ Bookings in the trailing 90 days, none `NO_SHOW`.
2. Call the calculator.
3. **Expect**: a computed buffer count of 0 — the flat-1 default does not apply once the
   5-sample threshold is met.

## Scenario 4 — Caps

1. A doctor with a very high no-show rate (e.g. 100%) and a large session.
2. Call the calculator.
3. **Expect**: the buffer count never exceeds 20% of the session's slots, and never exceeds 3,
   whichever is smaller.

## Scenario 5 — Even distribution unaffected

1. Any of the above scenarios, followed by actually generating the session's Slots.
2. **Expect**: however many buffer slots were computed, they are spread evenly across the
   session's timeline (018's existing, unchanged distribution logic).
