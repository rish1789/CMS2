# Feature Specification: Return to the Selected Clinic After Login

**Feature Branch**: `claude/070-login-return` | **Created**: 2026-10-01 | **Status**: Draft

**Input**: Phase 2R.2 of `docs/NEXT_PHASES_ACTION_PLAN.md`, live-audit finding 4: "Signing in loses the selected clinic and doctor."

## Context

A signed-out patient picks a doctor in discovery, which links to `/patient/clinics/{clinicId}?doctorId={doctorProfileId}`. `RequirePatientSession` redirects to `/patient/login` with the original location in `state.from`. Today, `PatientLoginPage` ignores it and always opens `/patient`. Signup does not carry the destination either, and its success screen doesn't even link back to login.

## Rules (source of truth)

1. **Destination source:** the guard's `state.from` (pathname + search + hash). If it is missing, use a `returnTo` query parameter, which survives a page refresh and the hop between login and signup.
2. **Accepted only if all of these hold:**
   - it is a string that starts with exactly one `/`, so `//` and `/\` are rejected;
   - it has no scheme, no backslash and no control characters;
   - its path is `/discover` or is under `/patient/` (or is exactly `/patient`);
   - its path is **not** `/patient/login` or `/patient/signup`, which would cause an authentication loop.
3. **Otherwise**, the destination is `/patient`. Direct login, with no destination, keeps going to `/patient`.
4. **Login → signup:** the "Create an account" link carries `returnTo`. The signup success screen offers "Log in", carrying the same `returnTo`. Signup does not log the patient in by itself; that is unchanged.
5. **Preserved:** the query string (notably `doctorId`) and the hash.

## User Scenarios

- **US1 (P1):** Discovery → pick a doctor → login → land on that clinic, with `doctorId` kept.
- **US2 (P1):** Discovery → pick a doctor → login page → "Create an account" → sign up → "Log in" → log in → land on that clinic and doctor.
- **US3 (P1):** Direct login lands on `/patient`. A malformed, external, protocol-relative or login/signup `returnTo` also lands on `/patient`, never outside the app or in a loop.

## Requirements

- **FR-001:** After a successful patient login, navigate to the validated destination (rules 1–3), using replace navigation.
- **FR-002:** Login → signup → login preserves the validated destination via `returnTo` (rule 4).
- **FR-003:** Validation is a single pure function with unit tests for every accepted and rejected form.
- **FR-004:** No backend change. No change to staff or Super Admin login (out of scope per the plan).

## Success Criteria

- **SC-001:** Both discovery journeys (US1, US2) end on the selected clinic, with the same `doctorId`, in 100% of the tested cases.
- **SC-002:** No tested input redirects outside the app or to `/patient/login` or `/patient/signup`.
