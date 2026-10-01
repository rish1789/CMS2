# Live software audit — 1 October 2026

Scope: the running frontend at http://localhost:5173 and backend at http://localhost:8080. Reviewed public discovery, patient login/dashboard/bookings/booking form, clinic administrator login/day sheet/doctor roster, and one doctor login/dashboard/onboarding page. Combined browser observations, small API checks, source review, and existing frontend checks.

No application code, bookings, clinical records, accounts, or clinic settings were changed. The rate-limit probe sent invalid empty signup bodies; no accounts were created. Credentials are intentionally omitted from this report.

## Status (updated 2026-10-01, IST)

| Finding | Status | Fix |
|---|---|---|
| 1. A no-show is shown as "Visit complete" | **Fixed** | Spec 069 (rish1789/CMS2#34): live-status text follows the patient's own outcome. |
| 2. A no-show stays "Your next visit" and shows as "Active" | **Fixed** | Spec 069 (#34): the server derives `visitOutcome`, and the next visit is chosen from upcoming outcomes only. |
| 3. Cancel is offered for ineligible appointments | **Fixed** | Spec 069 (#34): the server derives cancellation eligibility with a reason, and the endpoint still re-checks. |
| 4. Login loses the selected clinic and doctor | **Fixed** | Spec 070 (#36): the validated return path is carried through login and signup. |
| 5. A 429 is unreadable in the browser (CORS) | **Fixed** | Spec 071 (#37): CORS runs before the limiter on throttled paths, `Retry-After` is exposed, and signup shows rate-limit, unknown and network errors. |
| 6. Discovery stops after 20 results | **Fixed** | Spec 072 (this PR): paging controls, an `X-Total-Count` header and a unique tie-break sort. |
| 7. A doctor sees admin-only tools | Open | Phase 2R.5 |

The original observations below are preserved unchanged.

## Findings

### 1. P2 — A missed appointment is described as “Visit complete”

**Reproduced live.** The patient's October 1, 11:00 appointment showed **No-show** on the administrator's day sheet, but its patient detail screen showed **Schedule Status: Visit complete**. This incorrectly implies that a visit occurred.

Reproduce: open the patient's October 1 appointment under My bookings, then compare it with the corresponding doctor's October 1 day sheet.

Cause: `backend/src/main/java/com/cms/booking/api/PatientSessionLiveStatusController.java:62` uses the whole session's status for the patient's text. Line 74 maps session `COMPLETED` to “Visit complete”. `SessionLiveStatusService` treats no-show slots as resolved when determining whether the session is complete. That session outcome does not establish that this patient completed a visit.

Fix: distinguish the patient's visit outcome from the session's progress. Display a missed appointment as such, and reserve “Visit complete” for the patient's own completed slot.

### 2. P2 — Missed appointments remain “Your next visit”

**Reproduced live.** The same no-show appeared as **Active** in My bookings and **Your next visit — Today, 11:00** on the patient dashboard.

Cause: `frontend/src/features/patient-bookings/NextAppointmentCard.tsx:38` checks only booking `ACTIVE` and a date on or after today. It neither checks the slot outcome nor excludes elapsed appointment times. The patient booking summary exposes only `ACTIVE`/`CANCELLED`, so it cannot distinguish a completed or missed visit. My bookings renders that same limited status.

Fix: expose visit/slot status to the patient client and use it to choose upcoming appointments and display accurate history. Apply explicit rules for delayed appointments rather than treating all non-cancelled bookings today as upcoming.

### 3. P2 — Patient detail offers cancellation for ineligible appointments

**Reproduced in the UI; rejection verified in source.** The no-show appointment still had an enabled **Cancel booking** button. Its scheduled time had passed. No cancellation was submitted during the audit.

Cause: `frontend/src/routes/patient/PatientPages.tsx:121` mounts `CancelBookingButton` for every booking, without eligibility or status. The backend rejects patient cancellation inside the two-hour cutoff (`backend/src/main/java/com/cms/booking/api/PatientBookingCancellationController.java:84`) and rejects non-cancellable slot states. Queue bookings also have a separate self-cancellation restriction.

Impact: users are invited into a cancellation flow that cannot succeed, including for already-resolved appointments.

Fix: return cancellation eligibility and a reason with booking details; hide or disable the action and explain the applicable restriction.

### 4. P2 — Signing in loses the selected clinic and doctor

**Reproduced end to end.** While signed out, selecting a discovery result leads to patient login. Successful login then opens `/patient`, rather than returning to the selected clinic with its `doctorId` filter.

Cause: `frontend/src/routes/guards.tsx:22` preserves the original location in `state.from`, but `frontend/src/routes/patient/PatientLoginPage.tsx:17` unconditionally navigates to `/patient`.

Fix: restore the validated internal destination, including its query string, after authentication. Preserve it through signup too.

### 5. P2 — Rate-limit errors are unreadable by the browser

**Reproduced against the running API.** A bounded probe sent 31 empty JSON signup requests with `Origin: http://localhost:5173`. The first 30 returned HTTP 400 with `Access-Control-Allow-Origin: http://localhost:5173`. Request 31 returned HTTP 429 with `Retry-After: 59`, but **no Access-Control-Allow-Origin header**.

The browser therefore blocks access to the useful 429 response, and the UI receives a fetch failure instead of the server's “Too many requests” message.

Cause: `backend/src/main/java/com/cms/common/RateLimitingConfig.java:47` runs the throttle before Spring Security's CORS handling. `RateLimitingFilter.java:82` returns the response directly without passing through that CORS handling.

Fix: ensure CORS runs before the limiter, while preserving the allowed-origin policy. Expose `Retry-After` if the frontend needs a retry countdown.

Related source-confirmed issue: `frontend/src/features/patient-account/SignupForm.tsx:37` has no fallback branch for unknown API errors or `RATE_LIMIT_EXCEEDED`. Once the CORS issue is fixed (or under same-origin hosting), a parsed signup 429 can produce no visible error. Handle that code and add a default error message.

### 6. P2 — Discovery silently stops after 20 results

**Confirmed by the frontend/backend contract; the current dataset has only three results.** The backend defaults to 20 results and supports paging. The frontend has neither page/size parameters nor next-page controls. Matching doctors beyond the first page cannot be browsed under the same filters.

Evidence: `backend/src/main/java/com/cms/discovery/DiscoverySearchService.java:24` sets `DEFAULT_PAGE_SIZE = 20`; `frontend/src/features/discovery/api.ts:30` does not send pagination parameters; `DiscoverySearch.tsx:72` makes a single search request. Live GET requests with `size=1&page=0` and `size=1&page=1` successfully returned different doctors, confirming that server paging works.

Fix: add frontend paging with a total or `hasNext` response contract. Preserve a stable secondary sort when paging results with duplicate names.

### 7. P2 — Doctor dashboard exposes administrator-only tools

**Reproduced live; backend restriction verified in source.** The doctor sidebar hides Onboard staff and Booking protection, but the dashboard still displays both tiles. Clicking Onboard staff as a doctor opens a complete editable onboarding form. No onboarding request was submitted.

Cause: `frontend/src/routes/staff/ClinicToolsDashboard.tsx:273` renders all `LINKS` without role filtering. `frontend/src/routes/staff/ClinicToolPages.tsx:27` also renders the onboarding page without an appropriate role guard. The service correctly requires active ClinicAdmin membership (`backend/src/main/java/com/cms/identity/staff/service/StaffOnboardingService.java:78`).

Impact: doctors can spend time entering information before the backend rejects the operation. This finding is a UI authorization mismatch, not a demonstrated backend privilege bypass.

Fix: apply consistent role checks to dashboard tiles and route/page entry points, while retaining backend authorization.

## Verification and limits

- Frontend: **78 test files, 441 tests passed**.
- Production frontend build: **passed**, including TypeScript compilation. Vite reported a bundle above its 500 kB warning threshold.
- Frontend lint: completed successfully with warnings.
- Backend health: HTTP 200, `{"status":"UP"}`.
- Unauthenticated patient bookings: HTTP 401.
- Actuator environment endpoint: HTTP 403.
- Invalid discovery numeric parameter: HTTP 400.
- Patient, clinic administrator, and one supplied doctor account could sign in.
- Backend tests did **not run to completion**. After resolving the Gradle cache access issue, the build failed at `:processTestResources` with `AccessDeniedException` for `backend/build/resources/test`. This is a validation blocker, not evidence that backend tests fail.
- No booking creation/cancellation, clinical writes, staff onboarding, destructive operations, concurrent-write testing, cross-clinic isolation testing, or full security audit was performed. The second supplied doctor account was not needed for the observed flows. Passing frontend tests does not cover the integration bugs above.

Recommended order: correct patient visit status and action eligibility first; then restore login destinations, fix rate-limit error handling, and complete discovery pagination and role-aware navigation.
