# Quickstart: Booking Protection / Appointment Abuse Prevention

Validation scenarios proving the feature end-to-end, one per user story in spec.md. Run against the
local dev stack (`./dev.sh`) with a seeded patient account, clinic, and at least one bookable slot.
Request/response shapes referenced below are defined in `contracts/booking-protection.md`; field
definitions are in `data-model.md`.

## Prerequisites

- Backend and frontend running locally (`./dev.sh`), migrations applied.
- A Super Admin session (for Scenario 4).
- A ClinicAdmin session at a test clinic (for Scenarios 3 and 5).
- A self-service patient account with a valid session token (for Scenarios 1 and 2).

## Scenario 1 — Booking limit (User Story 1)

1. As the Super Admin, set `booking-limit.global-max-active` to `2` via
   `PUT /api/v1/admin/protection-settings/booking-limit.global-max-active`.
2. As the patient, book 2 active appointments at any clinic(s) — both succeed normally.
3. Attempt a 3rd booking. **Expect**: `409 BOOKING_LIMIT_REACHED`, with a message that states the
   limit was reached and does not name any specific other appointment or clinic.
4. Cancel one of the 2 active appointments.
5. Attempt a new booking again. **Expect**: succeeds (cancelled bookings never count — FR-003).
6. As the ClinicAdmin at a *different* clinic than where the patient's remaining appointment lives,
   set a per-clinic override of `1` via `PUT .../protection/limit-override`. Attempt a booking as
   the patient at that clinic while they already hold 1 active appointment elsewhere (still under
   the global cap of 2). **Expect**: `409 BOOKING_LIMIT_REACHED` at that specific clinic only — a
   booking attempt at a third, unrelated clinic still succeeds.

## Scenario 2 — Rate limiting (User Story 2)

1. As the Super Admin, set `rate-limit.max-attempts` to `3`, `rate-limit.window-minutes` to `5`,
   `rate-limit.cooldown-minutes` to `2` (small values for a fast manual check).
2. As the patient, make 3 booking attempts within a few seconds (any mix of success/failure is fine
   — FR-008 counts every attempt).
3. Make a 4th attempt immediately. **Expect**: `429 RATE_LIMITED` with a `retryAfterSeconds` value
   close to the configured cooldown.
4. Make a 5th attempt immediately after the 4th, still inside the cooldown. **Expect**: still `429`,
   with `retryAfterSeconds` counting down from the *original* trigger time, not reset by this new
   attempt (Clarifications — the cooldown end time is fixed once).
5. Wait past the cooldown; make another attempt. **Expect**: evaluated normally again (succeeds or
   fails for an unrelated reason, but not `429`).
6. Confirm via `GET .../protection/flags` (as ClinicAdmin, after the sweep runs — Scenario 3 covers
   triggering it manually if needed) that this burst does not, on its own, create more than the
   expected flags for a single episode (dedup rule).

## Scenario 3 — Admin flagging and review (User Story 3)

1. As the patient, generate flag-worthy activity at a test clinic: cancel bookings enough times to
   exceed `flagging.repeated-cancellations.threshold` within its window (default 4 within 30 days —
   lower the threshold via settings first for a fast check, as in Scenario 1 step 1).
2. Trigger `FlagDetectionService`'s sweep (either wait for its schedule, or invoke it directly if a
   manual-trigger test hook exists — size this in `tasks.md`).
3. As the ClinicAdmin at that clinic, call `GET /api/v1/clinics/{clinicId}/protection/flags`.
   **Expect**: one outstanding flag, `signalType: REPEATED_CANCELLATIONS`, with a `reason` string
   naming the actual count and window.
4. Call `GET .../flags/{flagId}`. **Expect**: `recentActivity.recentCancellations` lists this
   clinic's own cancellations for this patient; no other clinic's data appears.
5. As a ClinicAdmin at a *different* clinic, call the same `GET .../flags` for their own clinic.
   **Expect**: this flag does not appear (FR-022, BR-004) — unless the patient has also hit the
   global cap, in which case only the bare `atGlobalLimit: true` fact is visible via that other
   flag detail, never this clinic's cancellation list.
6. Call `POST .../flags/{flagId}/resolve`. **Expect**: `200`, flag now `RESOLVED` with
   `resolvedBy`/`resolvedAt` set to the calling admin and now. Confirm it no longer appears in the
   default (`status=OUTSTANDING`) list, but does appear with `status=RESOLVED` explicitly requested.
7. Confirm the patient's booking-limit and rate-limit behavior (Scenarios 1–2) are unaffected by
   having an outstanding or resolved flag (BR-003 — flags never enforce anything).

## Scenario 4 — Super Admin configuration (User Story 4)

1. As the Super Admin, call `GET /api/v1/admin/protection-settings` on a fresh environment (or after
   deleting all rows). **Expect**: every value returned with `isDefault: true` and the exact default
   values documented in `data-model.md`'s settings table.
2. `PUT` a new value for `booking-limit.global-max-active`. **Expect**: `200`, and the very next
   booking attempt anywhere in the system (Scenario 1) uses the new value with no restart.
3. `PUT` `booking-limit.enabled` to `false`. **Expect**: a patient already at the (now-irrelevant)
   limit can book normally; `rate-limit.enabled` and `flagging.enabled` remain independently in
   effect (FR-027).
4. As a patient or ClinicAdmin (not Super Admin), attempt either settings call. **Expect**: `403`.

## Scenario 5 — Per-clinic limit override authorization (User Story 5)

1. As a ClinicAdmin at Clinic A, `PUT .../protection/limit-override` for Clinic A. **Expect**: `200`.
2. As that same ClinicAdmin, attempt the same call for Clinic B (a clinic they have no role at).
   **Expect**: `403` or `404` per this codebase's existing cross-clinic-access convention.
3. Attempt to set `maxActiveAppointments` higher than the current global cap. **Expect**: `400`
   (BR-005).

## Regression checks (spec.md's Confirmed Existing Behavior section)

- The existing IP-only `RateLimitingFilter`-protected endpoints (staff login, patient login/signup,
  clinic registration) are untouched — confirm their existing tests still pass unmodified.
- No slot-generation, buffer-sizing, or availability calculation reads `Slot.status = NO_SHOW`
  through any new code path introduced by this feature — grep confirms `FlagDetectionService` is the
  only new reader, and it is read-only (BR-007).
