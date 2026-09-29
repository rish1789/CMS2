# API Layer — Contract, Error-Coverage, and Missing-Surface Findings

Field-name/type contract matches were verified as clean almost everywhere (see each finding's module's
"Verified Clean" list, summarized at the end of this file). The genuine defects at the API layer are
concentrated in three areas: incomplete error-code unions (beyond the shared patterns in
`00_cross_cutting_patterns.md`), reachability gaps (a working endpoint with no way for a real user to
reach it), and missing configuration UI. Findings that are instances of Pattern A/B/C are cross-referenced,
not repeated.

---

**[HIGH] - [WAITLIST_CLAIM] - [WORKFLOW_GAP]**
- Frontend: `frontend/src/features/waitlist/ClaimOfferCard.tsx:5-9` — `entryId: string` is a required prop with no default and no internal fetch. Repo-wide grep for `ClaimOfferCard` finds exactly one render site: its own test file (`ClaimOfferCard.test.tsx`, hardcoding `ENTRY_ID = 'entry-1'`).
- Backend: no `GET` endpoint exists anywhere under `com.cms.waitlist` that would let a patient enumerate their own waitlist entries (confirmed via package-wide grep for `@GetMapping`, zero hits). The only place `waitlistEntryId` is exposed at all is `InboxItemResponse.java:53`, inside the **staff-only** `/api/v1/clinics/{clinicId}/inbox` summary — and even there, `InboxItemCard.tsx:11-23` never reads it out, so staff can't discover it either.
- Break: `ClaimOfferCard` is fully implemented and unit-tested, but no real patient has any way to obtain the `entryId` needed to reach it. This is a workflow-completeness gap distinct from the general routing/app-shell issue (see `04_integration_gaps.md`) — the component is functionally unreachable even in principle, not merely unmounted.
- Fix: add `GET /api/v1/patients/waitlist-entries` (or `/mine`) returning `WaitlistEntryResponse[]` for the authenticated patient, plus a list view rendering `ClaimOfferCard` per `OFFERED` entry with its real id.
- **STATUS: FIXED, 2026-09-07.** Added `GET /api/v1/patients/waitlist-entries` (`PatientWaitlistController.listMine`, backed by a new `WaitlistEntryRepository.findByPatientAccount_IdOrderByJoinedAtDesc` query) plus an explicit `SecurityConfig` matcher (proactively, not reactively, per this codebase's own established discipline). New frontend `listMyWaitlistEntries()` client function and `MyWaitlistEntries.tsx` list view rendering a real, working `ClaimOfferCard` per `OFFERED` entry. Wired into the new app shell at `/patient/waitlist`.

**[HIGH] - [WAITLIST_JOIN staff] - [MISSING_STAFF_UI]**
- Backend: `StaffWaitlistController.java:31-40` exposes `POST /api/v1/clinics/{clinicId}/waitlist` (`StaffJoinWaitlistRequest`: `patientAccountId`, `doctorProfileId`, `specialization`) for Operations/ClinicAdmin to join a patient on their behalf.
- Frontend: repo-wide grep for this path and for `StaffJoinWaitlistRequest`/`StaffWaitlist` returns zero matches outside the backend — `frontend/src/features/waitlist/api.ts` only implements the patient self-service join.
- Break: this staff capability has no frontend client function or form anywhere.
- Fix: add a `staffJoinWaitlist()` client function and a staff-side form.
- **STATUS: FIXED, 2026-09-07.** Added `staffJoinWaitlist()` to `waitlist/api.ts` and a new `StaffJoinWaitlistForm.tsx`, wired at `/staff/clinics/:clinicId/waitlist/join` in the new app shell. Also found and fixed a Pattern-C instance in the same controller during this pass (see `00_cross_cutting_patterns.md`'s Pattern C status note) and added the missing `FORBIDDEN` case to `WaitlistJoinErrorBody`.

**[HIGH] - [APPOINTMENT_TYPE_CONFIG] - [MISSING_UI]**
- Backend: `BookingController.java:31-57` exposes `POST/GET /api/v1/doctors/{doctorProfileId}/appointment-types` and `PUT .../default-fee`.
- Frontend: whole-tree grep for `appointment-types`/`default-fee` returns zero hits outside the backend. Consequently `staff-booking/BookSlotForm.tsx:167-177`, `staff-booking/QueueBookSlotForm.tsx:168-178`, and `patient-booking/QueueBookSlotForm.tsx:85-96` all render a raw free-text "Appointment Type ID" input with no lookup — the operator/patient must already know the UUID out-of-band. Only `patient-booking/BookSlotForm.tsx:82-97` gets a real `<select>`, because `OpenSlotResponse` happens to embed `appointmentTypes` per slot.
- Fix: add a staff appointment-type management screen wired to the existing endpoints, and back the three raw-ID inputs with a select (or per-session lookup for the queue flows).
- **STATUS: PARTIALLY FIXED, 2026-09-07.** Added a new `appointment-types` feature (`api.ts`, `AppointmentTypeConfigForm.tsx` for list/create/set-default-fee, and a reusable `AppointmentTypeSelect.tsx`), wired at `/staff/clinics/:clinicId/doctors/:doctorProfileId/appointment-types`. Backed 2 of the 3 named raw-ID inputs with the real select: `staff-booking/BookSlotForm.tsx` and `staff-booking/QueueBookSlotForm.tsx` (both gained a new required `doctorProfileId` prop). The third, `patient-booking/QueueBookSlotForm.tsx`, was **not** backed — `GET .../appointment-types` is staff-JWT-gated only (`BookingSecurityConfig`'s `/api/v1/doctors/**` chain uses `StaffJwtAuthenticationFilter`), so a patient token can't call it; closing this one needs a genuinely new patient-facing endpoint (unlike Fixed-Time booking, no per-session appointment-type list exists for Queue mode), which is new API surface beyond this finding's literal ask - left as a documented remaining gap rather than invented under this pass.

**[MEDIUM] - [WAITLIST_CLAIM] - [NO_EXPIRY_VISIBILITY]**
- Frontend: `ClaimOfferCard.tsx` (whole file) never renders any expiry/countdown UI.
- Backend: `WaitlistEntryResponse.java:8-19` (the only DTO any patient-facing waitlist endpoint returns) does **not** include `offeredAt`/`offerExpiresAt`, even though `WaitlistEntry.offer()` (`WaitlistEntry.java:131-136`) stamps a real 30-minute deadline. The only place `offerExpiresAt` is ever serialized to any client is the staff-only `InboxItemResponse.java:55`.
- Break: this is not merely a missing frontend widget — the patient-facing contract has no field to source a countdown from at all. A patient with an OFFERED entry has zero visibility into how much of their 30-minute window remains.
- Fix: add `offerExpiresAt` to `WaitlistEntryResponse` (or the new list-entries endpoint's DTO from the finding above), then render a live countdown from it.
- **STATUS: FIXED, 2026-09-07.** Added `offeredAt`/`offerExpiresAt` to `WaitlistEntryResponse` (backend record + `.of()` factory) and the matching frontend type. `ClaimOfferCard.tsx` now takes an `offerExpiresAt` prop and renders a live `M:SS remaining`/`Expired` countdown via a small `useCountdown` hook; `MyWaitlistEntries.tsx` passes it through from the list.

**[MEDIUM] - [WAITLIST_JOIN] - [MISSING_ERROR_HANDLING]** *(instance of Pattern B, distinct trigger — see 00)*
- Frontend: `JoinWaitlistForm.tsx:93-99` (`doctorProfileId`) and `ClaimOfferCard.tsx:96-102` (`appointmentTypeId`) are plain free-text `<input>`s bound to backend `UUID`-typed fields, with no client-side format validation.
- Backend: a non-UUID string causes Spring to fail `@RequestBody` deserialization with `HttpMessageNotReadableException` before any feature code runs; none of the three `@RestControllerAdvice`s in the codebase (`WaitlistExceptionHandler`, `ScheduleExceptionHandler`, `GlobalExceptionHandler`) handle it, so Boot's default error body (`{"error":"Bad Request"}`, no `message`) is returned.
- Break: per Pattern B, both `defaultMessageFor`/`defaultClaimMessageFor` switches (`waitlist/api.ts:37-46,108-121`) have no default case, so this becomes `super(undefined)` → empty-string error → the `{error && (...)}` guard in both forms never renders. A mistyped id produces a completely silent failure: button re-enables, nothing shown.
- Fix: give both switches a `default:` fallback (see Pattern B), and/or replace both free-text id fields with real pickers (see the LOW finding below) so malformed ids can't reach the wire.
- **STATUS: PARTIALLY FIXED, 2026-09-07.** Both switches (`waitlist/api.ts`'s `defaultMessageFor`/`defaultClaimMessageFor`) gained `default:` fallbacks, closing the silent-failure outcome (see `00_cross_cutting_patterns.md`'s Pattern B status note). The free-text-to-picker part of the fix (the LOW finding below) was not done — left as a documented remaining gap.

**[MEDIUM] - [SCHEDULE_EDIT] - [ORPHANED_COMPONENT]**
- Backend: `ScheduleController.java:49-59` / `ScheduleService.java:77-99` implement a fully working, tested `PATCH` edit endpoint (re-runs auth/validation/overlap checks, non-retroactive per design).
- Frontend: `frontend/src/features/scheduling/ScheduleForm.tsx` is create-only — imports only `createSchedule`, has no `scheduleId` prop, no edit-mode state, no pre-filled fields, no way to select an existing schedule. `editSchedule()` is defined in `api.ts:99-129` but has zero callers anywhere in the repo.
- Break: unlike the general app-shell gap, this isn't "not wired up" — no component *exists* that could invoke the edit endpoint even if composed into a shell.
- Fix: add an edit affordance (an edit mode/prop on `ScheduleForm`, or a dedicated `EditScheduleForm.tsx`) calling the already-defined `editSchedule()`.
- **STATUS: FIXED, 2026-09-07.** Took the "edit mode/prop on `ScheduleForm`" option: added an optional `existingSchedule` prop that prefills the form and switches `handleSubmit` to call `editSchedule()` instead of `createSchedule()`, with mode-aware heading/button copy. No separate `EditScheduleForm.tsx` needed. (No schedule-picker screen was built to feed it a real `existingSchedule` yet - out of this finding's literal scope, which was specifically the missing edit-capable component.)

**[MEDIUM] - [STAFF_ONBOARDING] - [STATUS_CODE_GAP]**
- Frontend: `frontend/src/features/staff-onboarding/api.ts:34-42` (`OnboardStaffErrorBody`) and `:54-73` (`defaultMessageFor`, no default) omit `CLINIC_NOT_FOUND`.
- Backend: `StaffExceptionHandler.java:37-40` returns a real, reachable 404 `CLINIC_NOT_FOUND`, thrown by `StaffOnboardingService.java:89-91` when the path's `clinicId` doesn't resolve.
- Break: currently masked (backend always populates `message` for this exception, so Pattern A's precedence hides the gap) — but if that guarantee ever lapses, this becomes a live instance of Pattern B.
- Fix: add `CLINIC_NOT_FOUND` to the union and switch now, before it becomes a live bug.
- **STATUS: FIXED, 2026-09-07.** Added `CLINIC_NOT_FOUND` to `OnboardStaffErrorBody` and `defaultMessageFor`.

**[LOW] - [WALK_IN] - [MISSING_ERROR_HANDLING]**
- Frontend: `frontend/src/features/staff-booking/api.ts:42-46` (`WalkInErrorBody`) and `:87-98` (`defaultWalkInMessageFor`) have no case for `NOT_A_FIXED_TIME_SESSION`.
- Backend: `WalkInInsertionService.java:84-86` throws it; `BookingExceptionHandler.java:97-102` maps it to `409`.
- Break: masked today by Pattern A (backend message always present) — the raw internal string ("Session `<uuid>` is not a Fixed-Time session") leaks to staff instead of hand-written copy.
- Fix: add the case now, and apply Pattern A's precedence fix so it actually displays once added.
- **STATUS: FIXED, 2026-09-07.** Added `NOT_A_FIXED_TIME_SESSION` to `WalkInErrorBody` and `defaultWalkInMessageFor`; Pattern A's precedence fix (already applied to this file) means it now actually displays.

**[LOW] - [WAITLIST_JOIN] - [MISSING_ERROR_HANDLING]**
- Frontend: `WaitlistJoinErrorBody` (`waitlist/api.ts:22-25`) omits `PATIENT_ACCOUNT_NOT_FOUND` (thrown by `WaitlistJoinService.java:50-52`) and `DOCTOR_PROFILE_NOT_FOUND` (thrown by `WaitlistJoinService.java:55-58`) — both real, reachable codes.
- Break: masked today by Pattern A; not currently observable, but the TS union doesn't accurately model the endpoint's real error surface and would degrade to Pattern B's silent failure if the backend's message guarantee ever changes.
- Fix: add both codes to the union for type accuracy.
- **STATUS: FIXED, 2026-09-07.** Added `PATIENT_ACCOUNT_NOT_FOUND` and `DOCTOR_PROFILE_NOT_FOUND` to `WaitlistJoinErrorBody`.

**[LOW] - [WAITLIST_JOIN / WAITLIST_CLAIM] - [MISSING_UI]**
- Frontend: `JoinWaitlistForm.tsx:93-99` and `ClaimOfferCard.tsx:96-102` require the patient to type a raw UUID by hand for doctor id / appointment-type id, with no dropdown/autocomplete.
- Break: this is the direct usability root cause behind the MEDIUM silent-failure finding above.
- Fix: replace both free-text inputs with selects populated from existing doctor and appointment-type lookups.

**[LOW] - [CONSULTATION_NOTE] - [ERROR_CODE_OVERGENERALIZATION]**
- Frontend: `consultation-notes/api.ts:86-93` — `getConsultationNote`'s non-2xx branch defaults an unparseable JSON body to `{ error: 'CONSULTATION_NOTE_NOT_FOUND' }`.
- Break: the primary hypothesized bug (any 404 treated as "no note yet") does **not** exist — the mount-time check correctly compares the exact string `'CONSULTATION_NOTE_NOT_FOUND'` (`ConsultationNoteForm.tsx:35`), so a genuine `BOOKING_NOT_FOUND` is not swallowed. The remaining, narrower gap: if the response body itself fails to parse as JSON at all (empty body, HTML from a misconfigured proxy, an unhandled 500), the fallback silently defaults to the same "no note yet" code, hiding a real infrastructure failure as a blank slate.
- Fix: use a distinct sentinel (e.g. `error: 'UNKNOWN'`) for JSON-parse failures, and treat that as an error, not a blank slate.
- **STATUS: FIXED, 2026-09-07.** Added an `'UNKNOWN'` member to `ConsultationNoteErrorBody`; `getConsultationNote`'s JSON-parse-failure fallback now uses it instead of `CONSULTATION_NOTE_NOT_FOUND`. `ConsultationNoteForm.tsx`'s existing mount-time check (comparing the exact string `'CONSULTATION_NOTE_NOT_FOUND'`) already treats anything else as a real error with no further change needed.

**[LOW] - [PRESCRIPTION / EXTERNAL_RECORD_REFERENCE] - [ERROR_CODE_OVERGENERALIZATION]**
- Frontend: `prescriptions/api.ts:74-79,94-101` and `external-record-references/api.ts:61-69,85-93` default unparseable error bodies to `BOOKING_NOT_FOUND`.
- Break: unlike the consultation-note case, this doesn't hide the error (both forms' `.catch` unconditionally calls `setError`), but mislabels a genuine 500/malformed response with "This booking could not be found" instead of a generic failure message.
- Fix: same sentinel approach as above.
- **STATUS: FIXED, 2026-09-07.** Added an `'UNKNOWN'` member to both `PrescriptionErrorBody` and `ExternalRecordReferenceErrorBody`; all 4 JSON-parse-failure fallback sites (create+list in each file) now use it instead of `BOOKING_NOT_FOUND`.

**[LOW] - [PATIENT_SIGNUP] - [VALIDATION_DRIFT]**
- Frontend: `SignupForm.tsx:142` — hint text reads `"10-digit Indian mobile number, e.g. 98765 43210"` (with an internal space).
- Backend: `IndianMobileNumberValidator.java:19,25` — regex `^(?:\+91|0)?[6-9]\d{9}$` against `.trim()`ed input, which only strips leading/trailing whitespace, not an internal space.
- Break: a user who types the example literally submits a value the backend rejects, producing an avoidable `INVALID_MOBILE_NUMBER` error for input matching the app's own suggested format.
- Fix: change the hint to `"e.g. 9876543210"` (no internal space).
- **STATUS: FIXED, 2026-09-07.** Hint text corrected.

**[LOW] - [PATIENT_LOGIN] - [TYPE_MISMATCH]**
- Frontend: `LoginPatientErrorBody` (`patient-account/api.ts:62`) is typed as only `{ error: 'INVALID_CREDENTIALS'; message: string }`.
- Backend: `LoginRequest.java:5` + `PatientExceptionHandler.java:55-62` mean a blank email/password on `/login` actually returns `400 MISSING_REQUIRED_FIELD`, a shape the documented contract doesn't list for `/login` and the frontend type doesn't model.
- Break: no live bug today (`LoginForm.tsx:40` reads `err.body.message` directly, not `.error`, so it happens to display correctly regardless of the type gap) — but a future refactor that pattern-matches on `.error` would silently mishandle this case.
- Fix: broaden `LoginPatientErrorBody` to include the `MISSING_REQUIRED_FIELD` shape, mirroring `SignupPatientErrorBody`'s pattern.
- **STATUS: FIXED, 2026-09-07.** Broadened as suggested; `LoginForm.tsx`'s `err.body.message` read updated to handle the now-possible `undefined` case.

**[MEDIUM] - [STAFF_LOGIN] - [TYPE_MISMATCH]**
- Frontend: `staff-login/api.ts:17` — `LoginStaffErrorBody = { error: 'UNAUTHORIZED'; ... }`.
- Backend: `POST /api/v1/staff/login` never actually returns `"UNAUTHORIZED"` — `StaffExceptionHandler.java:42-46` returns `INVALID_CREDENTIALS` (401) for bad credentials, and `GlobalExceptionHandler.java:50-58` returns `MISSING_REQUIRED_FIELD` (400) for blank fields. `"UNAUTHORIZED"` belongs to a different, JWT-gated endpoint family (`StaffAuthenticationEntryPoint`).
- Break: currently harmless — `StaffLoginForm.tsx:41` reads `err.body.message` directly rather than branching on `.error` — but the type is factually wrong, and every *other* client in this module (`RegistrationForm`, `OnboardStaffForm`, `DeactivateStaffAction`) does branch on `.error`, so the same pattern applied here later would silently fail to match `INVALID_CREDENTIALS` against the wrongly-declared `UNAUTHORIZED`.
- Fix: correct the union to `{ error: 'INVALID_CREDENTIALS' | 'MISSING_REQUIRED_FIELD'; message?: string; field?: string }`.
- **STATUS: FIXED, 2026-09-07.** Corrected as suggested (as two discriminated members); the fallback body on an unparseable response body updated from `{error: 'UNAUTHORIZED'}` to `{error: 'INVALID_CREDENTIALS'}`.

**[LOW] - [CLINIC_REGISTRATION] - [MISSING_ERROR_HANDLING]**
- Frontend: `clinic-registration/api.ts:57-59` — `const body = (await response.json()) as RegisterClinicErrorBody` with no `try/catch`, unlike `staff-login/api.ts:37-42` and `staff-onboarding/api.ts:128-133,155-159`, which all wrap the equivalent parse safely.
- Break: a non-JSON error body (e.g. a proxy-level error page) throws a raw `SyntaxError` instead of the app's own error type. Not currently fatal — `RegistrationForm.tsx`'s outer catch has a generic fallback — but inconsistent with the rest of the codebase's own established pattern.
- Fix: wrap the parse in the same try/catch-and-fallback pattern used elsewhere in this module.
- **STATUS: FIXED, 2026-09-07.** Wrapped in a try/catch falling back to `{ error: 'REGISTRATION_FAILED' }`, matching the module's established pattern. Also fixed the related, more general `RegistrationForm.tsx` `applyApiError` silent-failure gap (see `00_cross_cutting_patterns.md`'s Pattern B status note) by adding a `default:` branch.

---

## Verified Clean (contract-level, no desync found) — summarized by module

- **Identity & Access**: every request/response field name/type across clinic registration, staff login, staff onboarding, staff deactivation, clinic verification, and doctor verification matches exactly; staff-deactivation's four error codes are all explicitly handled; storage-key hygiene (`cms.staffToken`/`cms.superAdminCredentials`/`cms.patientToken`) has no collisions; token-audience usage (Bearer JWT vs. HTTP Basic) is never crossed.
- **Patient identity & DPDP**: signup/login/anonymize request and response DTOs match field-for-field (no `id`-vs-`patientAccountId` or `token`-vs-`accessToken` drift); JWT audience scoping and token storage are correctly isolated from staff; anonymize idempotency and authorization gating are correct.
- **Discovery/Notification/Inbox**: discovery query param and response fields match exactly; all Inbox enum values (`InboxItemStatus`, `InboxItemType`) and the full `InboxItemResponse` envelope, including all three item types' per-type summary keys, match exactly; SSE frame format (event name, JSON shape) matches on both sides; all four Inbox error codes are correctly mapped.
- **Scheduling**: schedule create/edit field formats (day-of-week enum names, `LocalTime`/`LocalDate` strings, mode enum) all match Jackson's default (de)serialization exactly; `ScheduleResponse` fields match; the full schedule error-code set (7 codes + `UNAUTHORIZED`) is exactly and correctly handled; session-generation, slot-completion (aside from the `UNAUTHORIZED` gap in Pattern B), and session-delay's three-state branching (`applicable=false` / `delayMinutes=null` / `delayMinutes=N`) all correctly match backend-producible states.
- **Booking creation (staff/patient, fixed-time/queue)**: every request/response field name matches; `BigDecimal`/`int` JSON typing is safe (no Jackson customization exists, so numbers serialize as plain JSON numbers, matching frontend `number` typings); open-slot listing and post-booking local-state filtering are correct; patient-side error-code coverage is complete; staff-side coverage is complete except for the `UNAUTHORIZED` gap (Pattern B).
- **Booking operations (walk-in, queue position, cancellations)**: walk-in request shape (existing-vs-new-patient branching, always-optional `overrideReason`) is correct; queue-position response fields match; all three cancellation flows' response fields and pluralization/zero-count-is-success logic are correct; no leftover copy-pasted `SESSION_ALREADY_CANCELLED` handling in the partial-cancellation flow.
- **Waitlist**: the join form's XOR request-shape safety under stale radio-toggle state is correctly implemented (a fresh object literal per submit, never a stale spread); the claim endpoint's `BookingResponse`-vs-`WaitlistEntryResponse` type distinction (the two different response shapes for two related endpoints) is modeled correctly; decline's bodyless request and status handling are correct.
- **Clinical documentation**: all three features' request/response DTOs, routes, HTTP verbs, immutable create-only UI shape, and date formatting (`LocalDate` ↔ HTML `<input type="date">`) match exactly; the `instructions`-nullable-field handling on prescriptions is correct (no validation annotation exists on either side to violate).
