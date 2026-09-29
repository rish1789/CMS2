# 05 — API and Integration

Labels: [RUNTIME] = observed while running · [CODE] = read in source · [INFERRED] = drawn from code or docs, not proven · [UNKNOWN] = could not be determined.

- **Base path:** `/api/v1`, JSON over HTTP.
- **Inventory:** 109 request-mapping annotations across the backend `*Controller.java` files [CODE].
- **Live spec:** `http://localhost:8080/api-docs` answers 200 [RUNTIME].
- **Auth column:**
  - `staff` = staff JWT plus an active role at the clinic, checked in the service or controller
  - `patient` = patient JWT
  - `SA` = Super Admin JWT
  - `public` = no token
- **Allowlist column:** whether the path appears in its filter chain's explicit `authenticated()` list. "**NO**" means the request falls through to `anyRequest().permitAll()`. For all 7 such endpoints, an unauthenticated call was confirmed to return **500** [RUNTIME].

## 1. Endpoint catalogue

### Public and authentication

| Method | Path | Auth | Handler → service | Frontend consumer | Notes |
|---|---|---|---|---|---|
| POST | `/clinics/register` | public (rate-limited) | `ClinicRegistrationController` → `ClinicRegistrationService` | `features/clinic-registration/api.ts` | |
| POST | `/staff/login` | public (rate-limited) | `StaffAuthController` → `StaffAuthService` | `features/staff-login/api.ts` | Also Super Admin login. Distinct errors ACCOUNT_NOT_FOUND / INCORRECT_PASSWORD [RUNTIME]. |
| POST | `/patients/signup`, `/patients/login` | public (rate-limited) | `PatientAccountController` | `features/patient-account/api.ts` | |
| GET | `/discovery/search`, `/discovery/cities`, `/discovery/specializations` | public | `DiscoveryController` → `DiscoverySearchService` | `features/discovery/api.ts` | [RUNTIME 200]. `search` returns a **bare array**, paged internally. |

### Staff: clinic-scoped (`/clinics/{clinicId}/…`)

| Method | Path | Allowlist | Handler (service) | Consumer |
|---|---|---|---|---|
| GET | `/clinics/mine` | yes | `StaffClinicController` | staff-clinics |
| GET | `/doctors` | yes | `ClinicDoctorController` | doctor-picker |
| GET | `/doctors/booking-readiness` | **NO** | `DoctorBookingReadinessController` (repo-level) | doctor-picker |
| GET/POST/PATCH | `/doctors/{dp}/schedules[/{id}]` | yes | `ScheduleController` → `ScheduleService` | scheduling |
| DELETE | `/doctors/{dp}/schedules/{id}` | **NO** | `ScheduleDeletionController` → `ScheduleDeletionService` | scheduling |
| GET, POST | `/staff` | yes | `ClinicStaffController`, `StaffOnboardingController` | staff-picker, staff-onboarding |
| POST | `/staff/{acct}/deactivate` | yes | `StaffDeactivationController` | staff-picker |
| POST | `/staff/{acct}/reset-password`, `/set-password` | **NO** | `StaffPasswordResetController` | staff-picker |
| GET | `/sessions`, `/sessions/today-stats`, `/sessions/{sid}/day-sheet` | yes | `ClinicSessionListController`, `TodaySessionStatsController`, `SessionDaySheetController` | day-sheet, front-desk-walk-in |
| GET | `/sessions/{sid}/delay`, `/sessions/{sid}/live-status` | yes | `SessionDelayController` | session-delay |
| DELETE | `/sessions/{sid}` | **NO** | `SessionDeletionController` | session-cancellation |
| POST | `/sessions/{sid}/cancel`, `/cancel-from-cutoff`, `/bookings/cancel-batch` | yes | Session / partial / batch cancellation controllers | session-cancellation, partial-session-cancellation, day-sheet |
| POST | `/sessions/{sid}/queue-bookings` | yes | `StaffQueueBookingController` | staff-booking |
| POST | `/slots/{sid}/book`, `/appeared`, `/complete` | yes | `StaffBookingController`, `SlotAppearedController`, `SlotCompletionController` | staff-booking, day-sheet |
| POST | `/walk-ins` | yes | `FrontDeskWalkInController` | front-desk-walk-in |
| GET | `/bookings/{bid}` | **NO** | `BookingDetailController` | booking-detail |
| POST | `/bookings/{bid}/cancel` | yes | `StaffBookingCancellationController` | booking-cancellation |
| GET | `/bookings/{bid}/queue-position` | yes | `StaffQueuePositionController` | queue-position |
| GET/POST | `/bookings/{bid}/consultation-notes`, `/prescriptions`, `/external-record-references` | yes | clinical controllers | consultation-notes, prescriptions, external-record-references |
| GET | `/patients/search`, `/patients/{pid}`, `/patients/{pid}/bookings`, `/patients/today` | yes (the `/patients/*` wildcard also covers `today`) | patient record controllers | patient-search, PatientHubPage |
| POST | `/patients/{pid}/anonymize` | yes | `StaffPatientAnonymizationController` | patient-anonymization |
| POST | `/waitlist` | yes | `StaffWaitlistController` | waitlist |
| GET | `/waitlist/count` | **NO** | `StaffWaitlistController` | waitlist |
| GET/POST | `/inbox`, `/inbox/stream` (SSE), `/inbox/{id}/claim`, `/release`, `/resolve` | yes | `InboxController` | inbox |
| GET/POST | `/protection/flags[/{id}[/resolve]]` | yes | `ClinicProtectionFlagController` | clinic-protection |
| GET/PUT/DELETE | `/protection/limit-override[/history]` | yes | `ClinicBookingLimitOverrideController` | clinic-protection |

### Staff: doctor-scoped (`/doctors/{dp}/…`, chain 6, always authenticated)

| Method | Path | Consumer |
|---|---|---|
| GET/POST | `/appointment-types` | appointment-types |
| PUT | `/appointment-types/{id}` | appointment-types |
| PUT | `/default-fee` | appointment-types |

### Patient (`/patients/…`)

| Method | Path | Handler |
|---|---|---|
| GET | `/clinics` | `PatientClinicController` |
| GET | `/clinics/{id}/slots`, `/clinics/{id}/queue-sessions`, `/clinics/{id}/doctors` | `PatientBookingController` |
| GET | `/doctors/{dp}/appointment-types` | `PatientBookingController` |
| POST | `/clinics/{id}/slots/{sid}/book` | `PatientBookingController` → `PatientBookingService` |
| POST | `/clinics/{id}/sessions/{sid}/queue-bookings` | `PatientQueueBookingController` |
| GET | `/bookings`, `/bookings/{id}/queue-position`, `/bookings/{id}/live-status` | my-bookings, queue position, live status controllers |
| POST | `/bookings/{id}/cancel` | `PatientBookingCancellationController` (2 h cutoff lives here) |
| GET | `/bookings/clinical-record-availability`, `/bookings/{id}/consultation-note`, `/prescriptions`, `/external-record-references` | `PatientClinicalRecordController` |
| POST | `/clinics/{id}/waitlist` | `PatientWaitlistController` |
| GET | `/waitlist-entries` | `PatientWaitlistController` |
| POST | `/waitlist-entries/{id}/claim`, `/decline` | `PatientWaitlistClaimController` |

There is no `GET /patients/bookings/{id}` detail endpoint in the mapping list; the frontend detail page (`/patient/bookings/:bookingId`) is fed from the list response [INFERRED from the route plus the endpoint inventory].

### Super Admin (`/admin/…`, chain 3, always authenticated)

| Method | Path | Consumer |
|---|---|---|
| GET | `/clinics` (status, sort, page) | clinic-verification [RUNTIME 200] |
| POST | `/clinics/{id}/verify`, `/unverify`, `/reject`, `/restore`, `/reset-admin-password`, `/set-admin-password`; `/clinics/reject-bulk`, `/delete-bulk` | clinic-verification |
| DELETE | `/clinics/{id}` | clinic-verification |
| GET | `/doctors` | doctor-verification [RUNTIME 200] |
| POST, PATCH, DELETE | `/doctors/{id}/verify`, `/revoke`, `/reject`, `/restore`; `/doctors/reject-bulk`, `/delete-bulk`; `PATCH`/`DELETE /doctors/{id}` | doctor-verification |
| POST | `/sessions/generate` | session-generation |
| GET, PUT | `/protection-settings`, `/{name}`, `/{name}/history` | admin-protection-settings [RUNTIME 200, bare array] |
| POST | `/retention-purge/run`, `/rejected-purge/run` | **no frontend consumer** [CODE: 0 matches in `frontend/src`] |

## 2. Request and response conventions

- **Errors:** `ErrorResponse { error: CODE, message, failedRules[], field }` for handled exceptions [RUNTIME: 401 `{"error":"UNAUTHORIZED"}`, 403 FORBIDDEN, 401 INCORRECT_PASSWORD].
  - Unhandled exceptions → `ApiErrorController` → `{"error":"INTERNAL_SERVER_ERROR","message":"An unexpected error occurred. Please try again."}` [RUNTIME].
  - A malformed UUID in a path → 400 `{"error":"REQUEST_ERROR"}` [RUNTIME].
  - Rate limiting uses a separate `RateLimitedErrorResponse` shape (`BookingExceptionHandler.java:208`).
- **Lists: inconsistent envelopes [CODE and RUNTIME].**
  - Paged envelopes: `{clinics|doctors, page, pageSize, totalCount}` (admin lists), `{clinics: [...], page, pageSize, totalCount}` (`/clinics/mine`).
  - Bare arrays: discovery search, protection settings, booking readiness.
  - Spring `Page` mapped to custom records elsewhere (patient slots).
- **Validation:**
  - 35 `@RequestBody` parameters; **19** carry `@Valid`. The rest validate inside services with bespoke exceptions [CODE].
  - Bean-validation failures (`MethodArgumentNotValidException`) are handled only in `identity/api/GlobalExceptionHandler` and `patient/api/PatientExceptionHandler`.
- **Exception handling:** 12 `@RestControllerAdvice` classes; only 2 are package-scoped (`AdminExceptionHandler`, `PatientExceptionHandler`). The rest are global, and each module defines its own `ForbiddenException` (7 classes) or `NotStaffedAtClinicException` (5), so a handler matches by exact type [CODE].

## 3. Findings

| ID | Type | Finding | Evidence |
|---|---|---|---|
| API-01 | Weak auth wiring | 7 staff endpoints absent from the allowlist; unauthenticated requests return **500** instead of 401 | [RUNTIME] curl without a token: `GET /clinics/{c}/bookings/{b}`, `GET /doctors/booking-readiness`, `GET /waitlist/count`, `POST /staff/{a}/reset-password`, `POST /staff/{a}/set-password`, `DELETE /sessions/{s}`, `DELETE /doctors/{d}/schedules/{s}` → 500. Allowlisted neighbours → 401. → BUG-001 |
| API-02 | Inconsistent response format | Mixed list envelopes (§2) | as above |
| API-03 | Missing validation | 16 of 35 request bodies are not bean-validated; the walk-in email regex is hand-written in the service (`FrontDeskWalkInService.EMAIL`) | [CODE] |
| API-04 | Missing state validation | Staff booking, staff queue booking, patient queue booking and walk-in do not check session date or end; patient booking checks date only, not time | [CODE] PB-004, BUG-005 |
| API-05 | Error mapping | A DB constraint failure on the new patient's phone is reported as `SLOT_ALREADY_BOOKED` (staff booking) or not mapped (walk-in, staff queue) | [CODE] PB-001/002 |
| API-06 | Logic in controllers | Patient cancellation cutoff, clinic-scope filters and role checks sit in 26 controllers | [CODE] |
| API-07 | Unused endpoints | `/admin/retention-purge/run`, `/admin/rejected-purge/run` have no UI | [CODE]. Intended as operational triggers. |
| API-08 | Duplicated capability | Staff booking forms exist both as modals and as routes (`slots/:slotId/book`, `sessions/:sessionId/queue-book`); legacy redirect `sessions/:sessionId/walk-in` → `walk-in` | `App.tsx:136-139` |
| API-09 | Missing endpoints | No patient-account password reset or change, no notification preference update, no staff reactivation, no appointment-type delete, no manual no-show, no single-session time edit, no reschedule | [CODE] mapping inventory |
| API-10 | Frontend / backend mismatch risk | The frontend has **two HTTP layers**: `lib/apiClient.ts` (12 files) versus 73 raw `fetch` calls in 29 files, each with its own error parsing and its own `API_BASE_URL` (30 declarations). There is no shared 401 handling, so an expired 12-hour token surfaces as per-feature error text. | [CODE] |
| API-11 | Hand-built JSON | Notification payloads are built by string concatenation | [CODE] `SessionCancellationService` |
| API-12 | Public docs | Swagger UI and the OpenAPI spec are public | [RUNTIME `/docs` 302, `/api-docs` 200]. Fine for dev; a decision is needed for production. |
| API-13 | Account enumeration | Login distinguishes unknown account from wrong password | [RUNTIME] → SEC-11 |

## 4. Integrations

No outbound integrations exist [CODE]:
- Notification delivery is logged by `LoggingNotificationSender`.
- CORS is limited to `APP_CORS_ALLOWED_ORIGINS` (default `http://localhost:5173`). A preflight from a foreign origin got **403** [RUNTIME].
