# Research: Rejected Clinics Stop Operating (062)

All decisions below come from reading the current code on 2026-09-24. The spec has no remaining NEEDS CLARIFICATION markers.

## Decision 1 — One booking check, placed in the five services that create bookings

**Decision**: Refuse at the point where a Booking is created, in each of the five services that do it: `PatientBookingService.bookSlot`, `PatientQueueBookingService`, `StaffBookingService.bookSlot`, `StaffQueueBookingService.bookSlot`, `WalkInInsertionService.insertWalkIn`. Each one already loads the Slot/Session, so the check is `session.getClinic().isRejected()` → throw a new `ClinicNotAcceptingAppointmentsException` (409 `CLINIC_NOT_ACCEPTING_APPOINTMENTS`). It runs before any write, so no partial booking is left behind.

**Rationale**: These five are the only `bookingRepository.save*` sites (grep-verified). Waitlist claim (029) creates its booking through `PatientBookingService.bookSlot`, so it is covered with no change of its own; that satisfies FR-001's fifth path. Checking in the service, not the controller, satisfies FR-003 (server-enforced for every caller).

**Ordering (patient paths)**: in `PatientBookingService` and `PatientQueueBookingService`, the check runs *before* 060's `bookingProtectionService.checkAndRecordAttempt`. It uses a clinic lookup by the path `clinicId`; a slot or session belonging to a different clinic still fails later as not-found. Otherwise every attempt against a rejected clinic would write `booking_attempt_log` rows, and those rows block the guarded permanent-delete of that same rejected clinic. A refused attempt at a rejected clinic is not "booking activity" and must not count toward rate limits. On the staff paths, the check reads `session.getClinic()` once the slot or session is loaded.

**Alternatives considered**: A shared "booking guard" component — rejected (YAGNI; the check is a single condition). Checking in controllers — rejected; the waitlist claim path would bypass it.

## Decision 2 — Session generation skips rejected clinics at its single choke point

**Decision**: `ScheduleSessionGenerator.generateForSchedule` returns 0 when `schedule.getClinic().isRejected()`.

**Rationale**: `generateForSchedule` is the only session-creation path. Both the nightly trigger and the Super Admin manual trigger reach it through `SessionGenerationService.generate`. Existing future sessions are not deleted (bookings on them are cancelled, Decision 4; new ones are refused, Decision 1). On restore, the next run fills the full rolling horizon on its own, because generation is already idempotent per (schedule, date). That covers FR-005 with no code.

**Alternatives considered**: Filtering the schedule query in `SessionGenerationService.generate` — equivalent, but would leave `generateForSchedule` unguarded for any future caller.

## Decision 3 — Rejection publishes an event; the booking and waitlist modules react after commit

**Decision**: `ClinicVerificationService.rejectOne` publishes a new `ClinicRejectedEvent(clinicId, occurredAt)`, only on a genuine not-rejected → rejected transition (it is already idempotent). Two `@TransactionalEventListener(AFTER_COMMIT)` consumers:
- **booking** module: `ClinicRejectionCascadeService` cancels future bookings (Decision 4).
- **waitlist** module: expires the clinic's WAITING and OFFERED entries (FR-011) with one bulk update to the existing terminal `EXPIRED` status.

**Rationale**: This mirrors the existing `ClinicDeVerifiedEvent` → `DeVerificationCascadeListener` design exactly (Constitution III: cross-module effects go through events). Bulk reject calls `rejectOne` per clinic, so it publishes once per clinic with no extra work.

**Alternatives considered**: Reusing `DeVerificationCascadeService.cascadeFromClinic` — rejected: it routes Fixed-Time cancellations through the 025 path, which fires waitlist offers, and FR-009 forbids that. Its "future" query also has no date floor (Decision 4).

## Decision 4 — What "future booking" means, and how it is cancelled

**Decision**:
- **Scope**: a Booking with status ACTIVE whose Slot is still unresolved — `BOOKED` for fixed-time, or `OPEN` for a queue token (a real queue booking never flips its Slot to BOOKED; fixed 2026-09-24 after live verification found the gap) — and whose Session date is today or later. New repository query `findActiveUpcomingBookingsByClinic(clinicId, today)`. Slots already `APPEARED`/`COMPLETED`/`NO_SHOW` are never touched, and neither are sessions dated before today (FR-009: past bookings untouched).
- **Mechanism**: the existing reason-carrying `BookingRepository.cancelIfActive(id, reason, reasonDetail)` (it stamps `cancelledAt` itself) with the new reason `CLINIC_REJECTED`, then set the Slot back to `OPEN`. It does **not** go through `BookingCancellationService.cancel`, so no waitlist offer is ever triggered (FR-009).
- **Notice**: each cancelled booking whose patient has a Patient Account gets one notification event `BOOKING_CANCELLED_CLINIC_REJECTED` through the existing `NotificationEventService.publish`, like 008.

**Rationale**: "Still BOOKED on today-or-later" is exactly "an appointment that hasn't happened yet". `cancelIfActive` is already the race-safe, data-layer-guarded cancellation that bulk paths use (029/030/033); losing a race to a concurrent cancel skips that booking.

**Alternatives considered**: Cancelling by clock time (`now`) instead of date — rejected: queue-mode slots have no start time, and a same-day still-BOOKED appointment can't be served by a rejected clinic anyway.

## Decision 5 — The patient-console message comes from the cancellation reason

**Decision**: Add `CLINIC_REJECTED` to `BookingCancellationReason`. Add `cancellationReason` to `PatientBookingSummaryResponse` (the "My bookings" list). For that reason, the patient console keeps the "Cancelled" badge and adds the line "This clinic is no longer accepting appointments." under the booking details (a long message doesn't fit the status pill).
- `PatientBookingCancellationController` MUST reject `CLINIC_REJECTED` as a patient-supplied reason (it currently calls `valueOf` on raw input, which would otherwise accept it). The server returns the existing 400 `INVALID_CANCELLATION_REASON`.
- **No migration**: `booking.cancellation_reason` is a plain `VARCHAR(50)` with no check constraint (V31), and a new enum literal fits.

**Rationale**: It reuses the existing reason column and the existing patient screen; no new field or table (Constitution II). Other reasons stay patient-only, and this one stays system-only.

## Decision 6 — Staff access at a rejected clinic: only ClinicAdmin

**Decision**, in three places:
1. **Sign-in** (`StaffAuthService.login`): after the password matches, if the account has at least one active role assignment and **every** one of them is a non-ClinicAdmin role at a rejected clinic → throw `StaffClinicNotActiveException` (403 `CLINIC_NOT_ACTIVE`, "Your clinic is not currently active. Contact your clinic administrator."). Accounts with no assignments keep today's behavior. A multi-clinic account with any usable assignment signs in normally.
2. **Every clinic-scoped staff request** (`/api/v1/clinics/{clinicId}/**`): a new MVC `HandlerInterceptor`, `RejectedClinicAccessInterceptor`, in the staff realm. When the request is authenticated as staff and the path's `clinicId` names a rejected clinic, it refuses with 403 `CLINIC_NOT_ACTIVE` unless the caller holds an active ClinicAdmin assignment there. It reads the clinic by primary key first and only loads role assignments when the clinic is rejected, so non-rejected clinics cost one PK lookup.
3. **Clinic picker** (`GET /api/v1/clinics/mine`): a new repository query leaves out non-ClinicAdmin memberships at rejected clinics, so page totals stay correct.

**Rationale**: There are dozens of per-service "is caller staffed at this clinic" checks; editing each would be error-prone and easy to miss on the next feature. One interceptor keyed on the `{clinicId}` path variable is the single choke point (tenant-scoped by construction, FR-008). It also covers tokens issued before the rejection, which a login-only check would miss.

**Web-slice isolation**: the interceptor's `WebMvcConfigurer` gets its gate service through `ObjectProvider`, so existing `@WebMvcTest` contract slices (which load `WebMvcConfigurer` beans but not repositories) keep starting unchanged, with the gate inert there. This avoids repeating the codebase's known bug class of a JWT filter leaking into unrelated slices (004). Integration tests prove the gate against a real context.

**Alternatives considered**: Changing `RoleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue` itself — rejected: the ClinicAdmin exception and the membership listing need different semantics, and silently changing a query dozens of services rely on is riskier than one explicit gate. Blocking at login only — rejected (stale tokens).

## Decision 7 — Frontend

**Decision**:
- **Patient "My bookings"**: show the rejection message for `CLINIC_REJECTED`.
- **Staff sign-in form**: show the `CLINIC_NOT_ACTIVE` message.
- **Booking forms**: surface `CLINIC_NOT_ACCEPTING_APPOINTMENTS` through each form's existing server-error path. No new component.

**Rationale**: Minimal, existing surfaces only.
