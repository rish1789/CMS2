# 01 — Architecture

Labels: [RUNTIME] = observed while running · [CODE] = read in source · [INFERRED] = drawn from code or docs, not proven · [UNKNOWN] = could not be determined. See [00](00-PRODUCT-BASELINE.md).

## 1. System shape [CODE]

```
Browser (React SPA, :5173)
  ├─ Patient portal  /patient/**           ─┐
  ├─ Staff console   /staff/**              ├─ fetch() with "Authorization: Bearer <jwt>"
  ├─ Super Admin     /super-admin-console/** │   (token kept in sessionStorage, one key per realm)
  └─ Public pages    /, /discover, /register ┘
                     │
                     ▼
Spring Boot monolith (:8080)
  7 SecurityFilterChains (by path prefix) → @RestController → @Service → Spring Data JPA → PostgreSQL
                     │                                   │
                     │                                   └─ ApplicationEvents (AFTER_COMMIT listeners)
                     ├─ @Scheduled jobs (8), in-process
                     └─ SSE: /api/v1/clinics/{id}/inbox/stream (in-memory emitter registry)
```

- One deployable backend and one frontend bundle. There is no message broker, cache, external notification provider or object storage.
- `docker-compose.yml` defines only a Postgres service.

## 2. Backend architecture

### 2.1 Module layout [CODE]

Most modules follow `backend/src/main/java/com/cms/<module>/{api,domain,dto,exception,repository,service,config}`. Modules that don't:
- `identity/clinic` and `identity/doctor` put entities, services, repositories and exceptions in one package.
- `discovery` is a flat package.
- `common` holds cross-cutting infrastructure (CORS, rate limiting, request IDs, OpenAPI, error controller, mobile-number validator).

| Module | Responsibility | Size indicators |
|---|---|---|
| `identity` | Clinics, staff accounts, role assignments, doctor profiles, staff auth, Super Admin verification/rejection/purge | `ClinicVerificationService` 412 lines, `DoctorVerificationService` 379 lines |
| `patient` | Global patient accounts (`account`) and per-clinic patient records (`record`), linking, anonymization | |
| `scheduling` | Schedules, sessions, slots, session generation, no-show detection, auto-completion, delay and live status, check-in ("appeared") and completion | 7 controllers, 15 services |
| `booking` | Appointment types and fees, bookings (staff, patient, queue, walk-in), cancellations (individual, batch, session, partial), cascades, booking protection, day sheet | 22 controllers, 23 services |
| `waitlist` | Join, match, offer, claim, decline, expiry | |
| `clinical` | Consultation notes, prescriptions, external record references, retention purge | |
| `inbox` | Staff inbox items and SSE broadcast | |
| `notification` | `notification_event` persistence and delivery stub | |
| `discovery` | Public search over verified clinics and doctors | |
| `protection` | Protection settings, suspicious-activity flags, per-clinic booking-limit override | |
| `common` | Filters and config | |

Totals [CODE]:
- About 466 Java source files, about 22,500 lines.
- 109 request-mapping annotations.
- 25 `@Entity` classes.
- 40 Flyway migrations.

### 2.2 Layering as actually implemented [CODE]

The intended flow is `Controller → Service → Repository`. In practice:
- **26 controllers inject repositories directly.** Examples: `booking/api/PatientBookingCancellationController.java`, `booking/api/StaffBookingCancellationController.java`, `booking/api/SessionDaySheetController.java`, `patient/record/api/ClinicPatientSearchController.java`, `scheduling/api/ClinicSessionListController.java`.
  - These controllers perform authorization checks and some business rules themselves. For example, the 2-hour patient cancellation cutoff is `CUTOFF_HOURS = 2` in `PatientBookingCancellationController.java:32`.
- Services own their own authorization. There are **16 private `requireAuthorized`-style methods** and **37 calls** to `RoleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue`. There is no shared authorization component.
- No controller is `@Transactional`. Several controllers load an entity outside any transaction and hand it to a `REQUIRES_NEW` service. For example, `StaffBookingCancellationController` passes a `Booking` into `BookingCancellationService.cancel`.
  - This works because every `@ManyToOne` uses the default **EAGER** fetch: 40 relationships, 1 marked explicitly.
  - It also means each booking load pulls in slot → session → clinic → doctor profile → account → … [INFERRED performance cost].

### 2.3 Cross-module coupling [CODE]

`CLAUDE.md` and `README.md` state that cross-module effects go through events, not reach-through. Imports show heavy direct coupling, including cycles:

| From → To (number of `import com.cms.<to>` lines) |
|---|
| `booking` → scheduling 93, identity 79, patient 22, notification 4, protection 3, inbox 2, waitlist 1 |
| `identity` → booking 5, scheduling 4, waitlist 2, clinical 1, inbox 1, patient 1, protection 1 |
| `waitlist` → scheduling 14, identity 13, booking 6, patient 5, inbox 4, notification 3 |
| `clinical` → booking 17, identity 11 |
| `inbox` → identity 8, booking 2, waitlist 2 |
| `protection` → booking 8, identity 8, patient 3 |

Cycles present: booking↔identity, booking↔waitlist, booking↔inbox, booking↔patient.

Events **are** used for the flows listed in §2.4. Everything else reaches directly into other modules' repositories and services. Examples:
- `FrontDeskWalkInService` calls `QueueSlotService` (scheduling), `InboxItemService` (inbox) and `PatientRepository` (patient).

### 2.4 Event-driven flows [CODE]

| Publisher | Event | Listener(s) | Phase |
|---|---|---|---|
| `BookingCancellationService.doCancel` | `BookingCancelledEvent` | `waitlist/service/WaitlistBumpListener.onBookingCancelled`: offers the freed timed fixed-time slot to the longest-waiting match | AFTER_COMMIT |
| `ClinicVerificationService` (unverify) | `ClinicDeVerifiedEvent` | `booking/service/DeVerificationCascadeListener`: cancels future bookings, creates notification events and inbox notices | AFTER_COMMIT (`REQUIRES_NEW` inside, per HANDOFF Part 12) |
| `DoctorVerificationService` (revoke) | `DoctorLicenseRevokedEvent` | `DeVerificationCascadeListener.onDoctorLicenseRevoked` | AFTER_COMMIT |
| `ClinicVerificationService` (reject) | `ClinicRejectedEvent` | `ClinicRejectionCascadeListener` (bookings), `waitlist/service/ClinicRejectionWaitlistListener` | AFTER_COMMIT |
| `NotificationEventService.publish` | `NotificationEventPublishedEvent` | `notification/service/NotificationDeliveryListener` → `LoggingNotificationSender` (log only) | AFTER_COMMIT |

Consequence [CODE]: every caller of `BookingCancellationService` triggers a waitlist bump for timed fixed-time slots. Those callers are:
- patient cancellation
- staff single cancellation
- batch cancellation
- the de-verification cascade

Whole-session and partial cancellation do **not** go through this service. They call `bookingRepository.cancelIfActive` directly, so they do not bump the waitlist, as backlog 026/027 intend.

### 2.5 Scheduled jobs [CODE]

| Job | Schedule | What it scans |
|---|---|---|
| `NoShowDetectionTrigger` → `NoShowDetectionService` | every minute | **All** BOOKED, not-on-hold, timed fixed-time slots in the database, any date. Marks NO_SHOW once more than 10 minutes past start. |
| `SlotAutoCompletionTrigger` → `SlotAutoCompletionService` | every minute | **All** APPEARED timed fixed-time slots. Marks COMPLETED at the slot's end time. |
| `WaitlistExpirySweepTrigger` | every minute | Offers past their expiry |
| `NightlySessionGenerationTrigger` | 02:00 daily | Generates sessions 15 days ahead (`ScheduleSessionGenerator.HORIZON_DAYS = 15`) |
| `RejectedRecordPurgeTrigger` | 02:00 daily | Rejected clinics and doctors older than `ADMIN_REJECTION_RETENTION_DAYS` (30) |
| `RetentionPurgeTrigger` | 00:00 on the 1st of each month | Clinical-record retention purge |
| `BookingAttemptLogRetentionService` | every 24 h (fixed rate) | Booking attempt log |
| `FlagDetectionService` | every hour (fixed rate) | Suspicious-activity signals |

- `@EnableScheduling` lives on `scheduling/config/SessionGenerationSchedulingConfig.java` but applies application-wide.
- No distributed lock (for example ShedLock). Every instance would run every job [INFERRED from code; single instance today].
- All time comparisons use the JVM default time zone (see 08 and 07 PB-005).

## 3. Database architecture

- PostgreSQL. `spring.jpa.hibernate.ddl-auto: validate` means Hibernate checks the schema and Flyway owns it. `open-in-view: false`.
- 40 forward-only migrations `V1`–`V40` in `backend/src/main/resources/db/migration`.
- Primary keys are UUIDs (`gen_random_uuid()` in the database, `@UuidGenerator` in the entity).

Full model: [04-DATA-MODEL.md](04-DATA-MODEL.md).

## 4. API architecture [CODE]

- REST/JSON under `/api/v1/`, grouped by audience prefix:
  - `/clinics/**` — staff
  - `/patients/**` — patient
  - `/admin/**` — Super Admin
  - `/doctors/**` — staff, doctor-scoped configuration
  - `/staff/**` — staff login
  - `/discovery/**` — public
- Errors are a uniform `ErrorResponse(error, message, failedRules, field)` record. The same record is defined twice: `identity/api/dto/ErrorResponse.java` and `patient/api/dto/ErrorResponse.java`.
- There are 12 `@RestControllerAdvice` classes plus `common/ApiErrorController` as a last-resort handler, which returns a generic 500 body [RUNTIME: confirmed generic body].
- OpenAPI served at `/api-docs`, Swagger UI at `/docs` (unauthenticated) [RUNTIME: `/docs` returns 302 to the UI].

Full list: [05-API-INTEGRATION.md](05-API-INTEGRATION.md).

## 5. Authentication architecture [CODE]

| Realm | Token issuer | Filter | TTL | Secret |
|---|---|---|---|---|
| Staff | `identity/account/config/StaffJwtService` | `StaffJwtAuthenticationFilter` (`ROLE_STAFF`, principal = account UUID) | 12 h | `STAFF_JWT_SECRET`, random at startup if unset |
| Patient | `patient/account/config/JwtService` | `PatientJwtAuthenticationFilter` | 12 h | `PATIENT_JWT_SECRET` |
| Super Admin | `identity/admin/config/SuperAdminJwtService` | `SuperAdminJwtAuthenticationFilter` | 12 h | `SUPER_ADMIN_JWT_SECRET` |

- Staff and Super Admin both log in at `POST /api/v1/staff/login`. `StaffAuthService` resolves the Super Admin credential first ("SuperAdminResolvedLogin"). The response `role` is `STAFF` or `SUPER_ADMIN` [RUNTIME].
- Passwords use `BCryptPasswordEncoder` (`identity/account/config/SecurityConfig.java:26`).
- There is no refresh token, no server-side logout or revocation, and no MFA.

### Filter chains (order, matcher, default)

1. `/api/v1/clinics/**`: an explicit list of about 60 `authenticated()` matchers, then **`anyRequest().permitAll()`** (`identity/account/config/SecurityConfig.java:143-276`).
2. `/api/v1/patients/**`: about 20 `authenticated()` matchers, then **`anyRequest().permitAll()`** (`patient/account/config/SecurityConfig.java:85-148`).
3. `/api/v1/admin/**`: `anyRequest().authenticated()`.
4. `/api/v1/staff/**`: `permitAll()`.
5. `/api/v1/discovery/**`: `permitAll()`.
6. `/api/v1/doctors/**`: `anyRequest().authenticated()` using the staff JWT filter.
7. `/actuator/**`: `/actuator/health` permitted, everything else `denyAll()` [RUNTIME: health 200, env 403].

**Architectural weakness:** chains 1 and 2 fail open. Any new endpoint missing from the allowlist is reachable without a token. It is saved only if the controller calls `SecurityConfig.currentAccountId(...)`, which throws `IllegalStateException` and produces a 500. Seven such endpoints exist today [RUNTIME]; see 07 BUG-001 and 08 SEC-01.

## 6. Authorization [CODE]

- **Clinic tenancy:** each service loads the aggregate, filters it by `clinicId`, and requires an active role assignment at that clinic. For example, `SlotAppearedService.markAppeared` checks `slot.session.clinic.id == clinicId`, then Operations or ClinicAdmin.
- **Role matrix, spot-checked in code:**
  - Walk-in, staff booking, mark appeared: Operations or ClinicAdmin.
  - Complete visit: Operations or ClinicAdmin (BOOKED or APPEARED), or the treating Doctor (APPEARED only).
  - Clinical documents: the treating doctor only, through `TreatingDoctorAuthorizationService.requireTreatingDoctor`. This compares the account id only; it does **not** check that the doctor still has an active role at the clinic.
  - Staff cancel: any active role at the clinic, including Doctor.
- **Doctor-scoped configuration** (`/api/v1/doctors/{id}/appointment-types`, `/default-fee`): the doctor themself or a ClinicAdmin at **any** clinic where the doctor is active (`AppointmentTypeService.java:121-135`). Appointment types and fees are global per doctor, not per clinic.
- **Rejected-clinic gate:** `identity/account/config/RejectedClinicAccessInterceptor` together with `RejectedClinicAccessGate` blocks staff (except the ClinicAdmin) at rejected clinics.

## 7. Frontend architecture [CODE]

- `frontend/src/App.tsx` declares the routes: 3 guarded trees (`RequireStaffSession`, `RequirePatientSession`, `RequireSuperAdminSession` in `routes/guards.tsx`) plus public routes.
  - The guards check only that a token exists in `sessionStorage`. They do not check expiry.
- `routes/staff/ClinicShell.tsx` nests about 30 clinic tool routes under `/staff/clinics/:clinicId/*`.
- `features/<domain>/` typically holds `api.ts` (HTTP), `types.ts`, and one or more components.
- **State:** local `useState`/`useEffect` only. No React Query, Redux or context stores, apart from `Toast` context and session helpers in `features/*/token.ts`.
- **HTTP layer — two coexisting styles:**
  - `src/lib/apiClient.ts` `apiRequest<T>()` (typed, maps error bodies to `ApiError`), used in **12 files**.
  - Hand-written `await fetch(...)` in **29 files (73 call sites)**, each with its own error parsing.
  - `API_BASE_URL` is re-declared in **30 files**.
  - There is no central 401 handling.
- **Shared UI:** `src/components/` holds Button, Input, Select, Modal, Card, Badge, EmptyState, ListSkeleton, LoadingState, PaginationControls, SortableColumnHeader, Sidebar/SidebarDrawer, Toast, and more.
- **Styling:** Tailwind v4. Tokens override the built-in `indigo`/`gray`/`red`/`green`/`amber` scales and add a `cobalt` scale (`src/index.css`, `DESIGN.md`).

### Representative request flows

1. **Front-desk walk-in:**
   - `features/front-desk-walk-in/FrontDeskWalkInPage.tsx` (steps: `SessionStep` → `PatientStep` → reason)
   - → `features/front-desk-walk-in/api.ts`
   - → `POST /api/v1/clinics/{clinicId}/walk-ins`
   - → `booking/api/FrontDeskWalkInController`
   - → `booking/service/FrontDeskWalkInService.register`, which:
     - authorizes the caller
     - validates the visit reason
     - reuses or creates the `Patient`
     - `FeeResolutionService.resolve`
     - `QueueSlotService.issueNextWalkInSlot` (untimed slot, `token_number = max+1`, status BOOKED)
     - `Booking.walkIn(...)` `saveAndFlush`
     - `InboxItemService.createWalkInItem` (SSE push)
     - `QueuePositionService.positionOf`
   - → tables `patient`, `slot`, `booking`, `inbox_item`.
2. **Patient fixed-time booking:**
   - `features/patient-booking/OpenSlotList.tsx`
   - → `GET /api/v1/patients/clinics/{id}/slots?doctorProfileId&date` (`SlotRepository.findOpenFixedTimeSlots[OnDate]`)
   - → `POST /api/v1/patients/clinics/{id}/slots/{slotId}/book`
   - → `PatientBookingService.bookSlot`, which:
     - checks the rejected-clinic gate
     - runs `BookingProtectionService.checkAndRecordAttempt` (rate limit and active-booking cap)
     - checks slot OPEN and date not past
     - resolves the fee
     - `PatientLinkingService.findOrCreatePatient`
     - `Booking.bookedByPatient` `saveAndFlush` (partial unique index `uq_booking_slot_active` guards against a double booking)
     - sets slot → BOOKED
3. **Day sheet:**
   - `features/day-sheet/DaySheet.tsx` → `GET /clinics/{id}/sessions` (session list with counts)
   - → `SessionSlotsView.tsx` → `GET /clinics/{id}/sessions/{sid}/day-sheet`
   - Row actions:
     - `POST /slots/{id}/appeared`
     - `POST /slots/{id}/complete`
     - `POST /sessions/{sid}/bookings/cancel-batch`
     - links to the consultation-note, prescription and external-record routes

## 8. Real-time and live-update architecture [CODE]

| Surface | Mechanism | Interval |
|---|---|---|
| Staff inbox | SSE `GET /clinics/{id}/inbox/stream`. `InboxBroadcastService` keeps a `ConcurrentHashMap<clinicId, List<SseEmitter>>` with a 30-minute emitter timeout. The client reads SSE through `fetch` streaming so it can send the Authorization header (`features/inbox/api.ts:93-140`). | push |
| Queue position (patient and staff) | polling (`features/queue-position/QueuePositionIndicator.tsx:54`) | 20 s |
| Doctor live status / delay | polling (`features/session-delay/LiveScheduleStatusIndicator.tsx:74`) | 20 s |
| Front-desk session list and walk-in line | polling (`features/front-desk-walk-in/SessionStep.tsx:92`, `WalkInLinePanel.tsx:59`) | 20 s |
| Waitlist offer countdown | client timer | 1 s |

The SSE registry is per-JVM. With more than one backend instance, clients connected to one instance would miss items created on another [INFERRED].

## 9. External integrations [CODE]

- **None live.**
- Notification delivery is `LoggingNotificationSender`, which logs, with PII redacted unless `NOTIFICATION_LOG_PII=true`.
- No payment, SMS, email, storage or analytics providers.

## 10. Architectural decisions already present (preserve unless deliberately changed)

1. Three separate JWT realms with separate secrets. A token from one realm never satisfies another.
2. Role is resolved per request from `role_assignment`, not from a token claim, so deactivation takes effect immediately at every service check.
3. Database constraints as the concurrency guarantee:
   - `uq_booking_slot_active` (a slot's active booking)
   - `uq_slot_session_token` (queue token numbers)
   - the unique `booking_id` on `consultation_note`
   - `uq_suspicious_activity_flag_outstanding`
4. Write-once clinical documents: corrections are new records (constitution).
5. Fee locked on the booking at booking time (`booking.locked_fee`).
6. Forward-only Flyway migrations.
7. The walk-in line is untimed slots (`start_time IS NULL`) inside fixed-time sessions (063). Queue tokens are minted as BOOKED (064/V40).
8. Clinic verification gates **discovery**, not booking (backlog 002/005). Rejection gates both (062).
