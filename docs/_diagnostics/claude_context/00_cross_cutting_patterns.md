# Cross-Cutting Patterns — Read This File First

Nine independent module-scoped traces (8 feature areas + 1 full DB-schema pass) surfaced 38 individual findings.
Of those, ~16 are not 16 separate bugs — they are repeated symptoms of **3 root-cause patterns** baked into a
shared frontend code template. Fixing each pattern once (in a shared helper, or by a small backend change)
closes most instances at once instead of requiring one-off patches per feature. Read this file before
01–04; each per-finding entry in those files that is an instance of a pattern here says so and does not
repeat the fix.

---

## PATTERN A — `[SYSTEMIC] - [ERROR_DISPLAY] - [MESSAGE_PRECEDENCE_BUG]`

**Mechanism:** Nearly every frontend `api.ts` in this codebase follows the same template:

```ts
class SomeApiError extends Error {
  constructor(public body: SomeErrorBody) {
    super(body.message ?? defaultMessageFor(body));
  }
}
```

The intent is: "use the backend's message if it has one, otherwise fall back to hand-written copy."
In practice, **every backend exception handler in this codebase always populates `message`**
(`ErrorResponse.of(code, e.getMessage())`, and every domain exception's constructor sets a message).
So `body.message` is *always* truthy, and `defaultMessageFor(body)` — the carefully-worded,
feature-specific copy — is **permanently dead code** for every real, successfully-JSON-parsed error
response. Users see raw backend exception text instead, which is sometimes wrong-domain (Pattern C)
and sometimes UUID-laden internal detail never meant for an end user.

**Confirmed sites (backend `message` always wins, hand-written frontend copy never shows):**
- `frontend/src/features/consultation-notes/api.ts:24`
- `frontend/src/features/prescriptions/api.ts:40`
- `frontend/src/features/external-record-references/api.ts:28`
- `frontend/src/features/scheduling/api.ts:41` (e.g. surfaces `"New Schedule overlaps existing Schedule 3fa85f64-…"` verbatim instead of "This overlaps another schedule this doctor already has…")
- `frontend/src/features/session-delay/api.ts:29-34` and `:46-57`
- `frontend/src/features/patient-anonymization/api.ts:21` (compounds with Pattern C below)

**Fix (one-line change, apply everywhere the template appears):** flip precedence —
`super(defaultMessageFor(body) ?? body.message)` — so curated copy wins when it exists, and the raw
backend message is only a last resort. Reserve `body.message` for a developer console log, not the
user-facing string.

**STATUS: FIXED, 2026-09-07.** Precedence flipped in all 17 `api.ts` files using this template
(the 6 originally-confirmed sites plus 11 more found to share the identical shape): `consultation-notes`,
`prescriptions`, `external-record-references`, `scheduling`, `session-delay` (both classes),
`patient-anonymization`, `staff-booking/api.ts` (both classes), `staff-booking/queueApi.ts`, `inbox`,
`waitlist/api.ts` (both classes), `partial-session-cancellation`, `session-cancellation`,
`booking-cancellation`, `queue-position`, `patient-booking/api.ts`, `patient-booking/queueApi.ts`,
`staff-onboarding` (both classes). The 3 Super-Admin/HTTP-Basic `AdminApiError` clients
(`clinic-verification`, `doctor-verification`, `session-generation`) were left untouched — they use a
status-code `if/else` chain with an unconditional final fallback, not this switch-based template, so
Pattern B's empty-string failure mode doesn't apply there.

---

## PATTERN B — `[SYSTEMIC] - [ERROR_DISPLAY] - [MISSING_DEFAULT_CASE_SILENT_FAILURE]`

**Mechanism:** This is the more severe twin of Pattern A. When the backend returns an error body with
**no `message` field at all** — which happens for exactly three real, guaranteed-to-occur cases:
1. `StaffAuthenticationEntryPoint`/`PatientAuthenticationEntryPoint`'s `401 {"error":"UNAUTHORIZED"}` (no `message` key), fired whenever a bearer token is missing, expired, or malformed — an ordinary, frequent occurrence (12h staff token TTL), not an edge case.
2. Spring's default `HttpMessageNotReadableException` body when a request field fails to deserialize (e.g. a non-UUID string submitted for a `UUID`-typed field) — `{"error":"Bad Request"}`, again no `message`.
3. Any other exception type a given feature's error-code TypeScript union simply forgot to declare.

— `body.message` is `undefined`, and `defaultMessageFor`'s `switch` statement has **no `default:` branch**,
so it also returns `undefined`. `new Error(undefined)` in JavaScript produces an `Error` whose `.message`
is the **empty string**, not `"undefined"`. Every component in this codebase gates its error banner with
`{error && (<p>...</p>)}` — and `''` is falsy in JS. **The alert never renders.** The submit button simply
re-enables. The user sees nothing happen at all, with no way to know why.

**Confirmed sites where this exact chain was traced end-to-end and produces a real silent failure:**
- `[HIGH] - PATIENT_ANONYMIZATION - SILENT_FAILURE` — `frontend/src/features/patient-anonymization/api.ts:12-15,27-36` has no `UNAUTHORIZED` case; documented as a normal expected response in `specs/037-patient-immediate-anonymization/contracts/patient-anonymization.md:25`.
- `[HIGH] - STAFF_BOOKING - MISSING_ERROR_HANDLING` — `frontend/src/features/staff-booking/api.ts:33-40,68-85` has no `UNAUTHORIZED` case. **The patient-facing equivalent (`patient-booking/api.ts:56-61,83-84`) already handles this correctly** — the fix pattern exists a few files away in the same codebase.
- `[HIGH] - QUEUE_BOOKING - MISSING_ERROR_HANDLING` — `frontend/src/features/staff-booking/queueApi.ts:24-32,44-63`, same gap, same correct sibling exists at `patient-booking/queueApi.ts:22-27,52-53`.
- `[HIGH] - SLOT_COMPLETION - MISSING_ERROR_HANDLING` — `frontend/src/features/session-delay/api.ts:18-22,46-57`; `CompleteSlotButton.tsx:26-31` never checks for it, unlike the sibling `ScheduleForm.tsx:78-86`, which explicitly detects `UNAUTHORIZED` and forces re-login — again, the correct pattern exists next door.
- `[HIGH] - CLINIC_REGISTRATION - SILENT_FAILURE` — `frontend/src/features/clinic-registration/RegistrationForm.tsx:49-72`'s `applyApiError` has no `default` branch at all for *any* unrecognized code, the most general form of this bug. **STATUS: FIXED, 2026-09-07** — added a `default:` branch setting a generic error message.
- `[MEDIUM] - WAITLIST_JOIN/CLAIM - MISSING_ERROR_HANDLING` — `frontend/src/features/waitlist/api.ts:37-46,108-121` triggered not by an expired token but by a **user typing a non-UUID string** into the free-text "doctor id"/"appointment type id" inputs (`JoinWaitlistForm.tsx:93-99`, `ClaimOfferCard.tsx:96-102`) — Spring's default deserialization-error body has no `message`, same silent chain.

**Pattern within the pattern:** every case where this bug is *absent* is a case where a developer happened
to remember to add an `UNAUTHORIZED`/`default` branch by hand. There is no shared error-handling utility
enforcing completeness — it is pure per-file diligence, which is why it's present in roughly half the
`api.ts` files and absent in the other half.

**Fix:**
1. Add a `default:` case to every `defaultMessageFor`-style switch, returning a generic
   `"Something went wrong. Please try again."` — this alone stops the empty-string/silent-failure outcome everywhere, even for codes nobody thought to enumerate.
2. Add an explicit `UNAUTHORIZED` case (with a "Your session has expired, please log in again" message,
   mirroring `ScheduleForm.tsx:78-86`'s existing correct handling) to every staff-facing and patient-facing
   `api.ts` that doesn't already have one.
3. Longer-term: extract one shared `apiErrorMessage(body, specificMessages)` helper used by every feature,
   so this can't regress per-file again.

**STATUS: FIXED, 2026-09-07.** `default:` cases (generic "Something went wrong. Please try again.")
added to every switch in the same 17 files listed under Pattern A's status note. Explicit `UNAUTHORIZED`
cases (with re-login-forcing messages) added to every switch that lacked one:
`consultation-notes`, `prescriptions`, `external-record-references`, `session-delay`
(`SlotCompletionErrorBody` and, newly, `SessionDelayErrorBody`), `patient-anonymization`,
`staff-booking/api.ts` (both), `staff-booking/queueApi.ts`, `inbox`, `waitlist/api.ts` (both classes,
plus a `FORBIDDEN` case the staff-join addition needed), `partial-session-cancellation`,
`session-cancellation`, `booking-cancellation`, `queue-position`. Also wired the actual
force-re-login *behavior* (not just the message) into `CompleteSlotButton.tsx` and
`AnonymizePatientButton.tsx`, mirroring `ScheduleForm.tsx`'s existing correct pattern exactly.
The 5 named "currently masked" gaps in `02_api_layer.md` were closed too (see that file's own
STATUS notes). The longer-term shared-helper suggestion (item 3) was not built — the per-file
`default:`+`UNAUTHORIZED` fix already closes every concrete instance found.

---

## PATTERN C — `[SYSTEMIC] - [AUTHORIZATION] - [WRONG_DOMAIN_ERROR_MESSAGE]`

**Mechanism:** Several unrelated backend features throw `com.cms.scheduling.ForbiddenException` — a class
originally written for the 013-recurring-schedule-definition feature, with a **hardcoded** message:

```java
// backend/src/main/java/com/cms/scheduling/ForbiddenException.java:7
super("Not authorized to manage schedules for this doctor at this clinic")
```

Because Pattern A means the frontend always displays the backend's raw message, staff users doing
something that has nothing to do with schedules see this exact string on a 403.

**Confirmed sites reusing this exception (and therefore this exact wrong-domain message):**
- `TreatingDoctorAuthorizationService` (`com.cms.clinical`), thrown for consultation-note, prescription, and
  external-record-reference authorization failures — surfaced via `ScheduleExceptionHandler.java:13-15`
  reused globally as a `@RestControllerAdvice`.
- `StaffPatientAnonymizationController.java:38-47` (`com.cms.patient.record`), same exception, same message,
  for a patient-anonymization 403.

**Fix:** give each consuming module its own `ForbiddenException` with a message appropriate to that
action (e.g. "Only the treating doctor may write or view this note.", "Only Operations or ClinicAdmin
staff may anonymize a patient." — both of these correct strings **already exist** as the dead
`defaultMessageFor` fallback text in each feature's `api.ts`; they just never get a chance to display
because of Pattern A). Fixing Pattern A's precedence bug alone would already surface the *existing*
correct frontend copy in these cases without touching the backend at all — the two patterns compound,
and fixing either one independently improves the outcome.

**STATUS: FIXED, 2026-09-07.** Added `com.cms.clinical.ForbiddenException`,
`com.cms.patient.record.ForbiddenException`, and `com.cms.waitlist.ForbiddenException`, each with its
own message, each registered in that module's own `@RestControllerAdvice`. Updated the throw sites
(`TreatingDoctorAuthorizationService`, `StaffPatientAnonymizationController`) to use the local class.
Also found and fixed a **third, previously-undocumented** reuse site during this pass:
`StaffWaitlistController.java` (031 US2, staff-joins-a-patient-to-the-waitlist) was throwing the same
`com.cms.scheduling.ForbiddenException` for the same reason — fixed identically. Full backend build
(compile + spotless) green; the one non-Docker test failure found in the full suite
(`PatientAccountContractTest`, a pre-existing `@WebMvcTest`-slice bean-wiring gap unrelated to any of
this session's edits) does not touch any of these three modules.

---

## Why these three patterns account for most of the audit's volume

15 of the 38 tagged findings across `01`–`04` are direct instances of A, B, or C. The remaining ~23 are
genuinely independent issues (workflow-completeness gaps, one real database-level bug, missing UI,
orphaned components) and are documented individually in the other files.
