# Quickstart: Rejected Clinics Stop Operating (062)

Live-verification scenarios against the running dev stack (`backend` + `frontend` from `.claude/launch.json`). Super Admin login: `super-admin` / `SuperAdmin!2026`.

## Setup (throwaway data only)

1. Register a new clinic (`POST /api/v1/clinics/register`); keep the ClinicAdmin email and password.
2. As that ClinicAdmin, onboard a Doctor, give it a default fee and an appointment type, and create a Fixed-Time schedule covering today and the next few days.
3. Run session generation (the Super Admin manual trigger) so sessions and slots exist.
4. Sign up a patient account and book one Fixed-Time slot for a later date. Add a walk-in on another slot, and join the doctor's waitlist with a second patient.

## Scenario 1 — Rejection cancels upcoming bookings (FR-009, FR-010)

Reject the clinic from the Super Admin console. Then:
- Both upcoming bookings are `CANCELLED` with reason `CLINIC_REJECTED`, and their slots are `OPEN`.
- The patient's "My bookings" row shows Cancelled with the line "This clinic is no longer accepting appointments."
- No waitlist offer was created.

## Scenario 2 — Waitlist closed (FR-011)

The second patient's waitlist entry is `EXPIRED`.

## Scenario 3 — Every booking path refused (FR-001, FR-002)

With the clinic rejected, attempt each path. Each returns `409 CLINIC_NOT_ACCEPTING_APPOINTMENTS` and writes no booking:
- patient fixed-time booking
- patient queue booking (needs a Queue schedule; optional live, covered by integration test)
- ClinicAdmin staff-assisted booking
- ClinicAdmin walk-in

## Scenario 4 — No new sessions (FR-004)

Run the manual session-generation trigger. No session is created for the rejected clinic; other clinics' counts are unchanged.

## Scenario 5 — Staff access (FR-007)

- **Doctor** login → `403 CLINIC_NOT_ACTIVE`; the sign-in form shows the message.
- **Doctor's pre-rejection token** on any `/api/v1/clinics/{id}/...` request → `403 CLINIC_NOT_ACTIVE`.
- **ClinicAdmin** login → succeeds, and `/clinics/mine` still lists the clinic. Browsing works; booking is refused (Scenario 3).

## Scenario 6 — Restore (FR-005)

Restore the clinic. Then:
- The Doctor can sign in again.
- A new booking succeeds.
- The next generation run fills the rolling window.
- The bookings cancelled in Scenario 1 stay cancelled.

## Scenario 7 — Pending clinics unchanged (FR-006)

Against any pending clinic, every booking path and generation behaves as before (full existing test suites green).

## Cleanup

Reject the throwaway clinic. The guarded permanent-delete will refuse it (it has sessions), so remove it by the same one-off SQL used on 2026-09-24, or leave it.

## Automated coverage

- Backend unit and contract: `gradle test --tests "*.unit.*" --tests "*.contract.*"`
- Backend integration (Docker/Testcontainers): the `*RejectedClinic*` integration classes
- Frontend: `npx vitest run`, `npx tsc -b`, `npm run lint`
