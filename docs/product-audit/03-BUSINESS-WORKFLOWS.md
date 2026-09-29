# 03 — Business Workflows

Labels: [RUNTIME] = observed while running · [CODE] = read in source · [INFERRED] = drawn from code or docs, not proven · [UNKNOWN] = could not be determined.

Unless tagged otherwise, every trace below is [CODE]. Staff and patient UIs could not be driven at runtime (see 00 §16).

## 0. Core concepts as the code defines them

These are not interchangeable.

| Concept | Where it lives | What it means in this system |
|---|---|---|
| **Clinic** | `clinic` | The tenant |
| **Patient account** | `patient_account` | A global login identity, shared across clinics |
| **Patient** | `patient` | A **per-clinic** record, optionally linked to one patient account (`patient_account_id`). Walk-ins can exist without an account. |
| **Doctor** | `account` + `role_assignment(role=Doctor)` + one global `doctor_profile` | Specialization, licence, experience, visibility |
| **Schedule** | `schedule` + `schedule_day` | A doctor's recurring weekly availability at one clinic, in FIXED_TIME or QUEUE mode |
| **Session** | `session` | One concrete date of a schedule (a snapshot of times and mode). Has **no status column.** |
| **Slot** | `slot` | A unit of capacity inside a session: a timed slot (fixed-time), a token (queue; `token_number`), or an untimed walk-in line place (fixed-time session, `start_time IS NULL`). **Its `status` also carries the visit state:** OPEN → BOOKED → APPEARED → COMPLETED, or NO_SHOW. |
| **Booking / appointment** | `booking` | Binds a patient to a slot, with appointment type, locked fee, source (SCHEDULED or WALK_IN) and a status of ACTIVE or CANCELLED only |
| **Visit** | *(no entity)* | The combination of an ACTIVE booking and its slot's APPEARED/COMPLETED status. Clinical documents hang off `booking_id`. |
| **Queue** | QUEUE-mode session + token slots; "position" is derived by `QueuePositionService` | |
| **Walk-in** | `booking.source = WALK_IN` | A token (queue session) or an untimed line place (fixed-time session) |
| **Buffer** | *(removed)* | `slot.is_buffer` dropped in V35 (spec 058) |
| **No-show** | `slot.status = NO_SHOW` | Set automatically. The booking stays ACTIVE. |

---

## W1. Patient registration (online account)

Patient → `/patient/signup` (`features/patient-account`) → `POST /api/v1/patients/signup` → `PatientAccountService`:
- password policy
- Indian mobile check
- email unique across both the patient and staff realms

→ `patient_account` row. **Resulting state:** a global account with no clinic records yet.

Login: `POST /patients/login` → a 12-hour JWT in `sessionStorage`.

- No per-clinic `patient` record is created at signup. One is created lazily at first booking (W4b).
- Gaps: no email verification, profile edit, password change or reset (02 #32, #45).

## W2. Existing patient search (staff)

Operations or ClinicAdmin → `features/patient-search/PatientSearch.tsx` or `PatientPicker.tsx` → `GET /clinics/{id}/patients/search?q=` (paged) → `ClinicPatientSearchController` → `PatientRepository`, clinic-scoped.

- Also available: `GET /clinics/{id}/patients/today` (today's roster) and `GET /patients/{pid}` with `/bookings` for the hub (`routes/staff/PatientHubPage.tsx`).
- **Resulting state:** read-only.
- Anonymized patients are returned with `anonymizedAt` set; the walk-in step filters them out client-side.

## W3. New patient registration (staff side)

There is **no standalone "register patient" action.** A clinic `patient` row is created only as a side effect of a booking.

| Path | Code | Dedupe behaviour |
|---|---|---|
| Staff fixed-time booking with a new patient | `StaffBookingService.resolveOrCreatePatient` → `patientRepository.save(new Patient(...))` | None server-side. Relies on the partial unique index `uq_patient_clinic_phone_unlinked`. A collision surfaces at `bookingRepository.saveAndFlush` and is caught as `SlotAlreadyBookedException` (PB-001). |
| Staff queue booking with a new patient | `StaffQueueBookingService:120` | Same index; no handler (PB-002) |
| Walk-in with a new patient | `FrontDeskWalkInService.register` | The client looks up the phone and offers the existing record (`PatientStep.tsx:22-30`). Server-side there is only the index, with no handler (PB-002). |
| Patient online booking | `PatientLinkingService.findOrCreatePatient` | Reuses the linked record, else links an unlinked record with a matching phone, else creates one |

**Broken or incomplete transition:** the staff paths and the patient path use different identity rules. Staff can create an unlinked record with a phone number that a later online booking will silently link (FR-003 of spec 009). That may be intended, but staff never see it happen.

## W4. Appointment booking

### W4a. Staff-assisted fixed-time booking

Operations or ClinicAdmin → day sheet or doctor list → `BookSlotForm` (modal) or route `slots/:slotId/book?doctorProfileId=` → `POST /clinics/{id}/slots/{slotId}/book` → `StaffBookingService`, which:
- authorizes the caller
- checks the slot is in this clinic and OPEN
- resolves the fee (hard block if none)
- resolves or creates the patient
- `Booking(...)` `saveAndFlush`
- sets slot → BOOKED

**Result:** `booking` ACTIVE (SCHEDULED, `booked_by_account_id`, `payment_status` PENDING); slot BOOKED.
- **No check of `session_date`** against today, so a past slot that is still OPEN can be booked (PB-004).
- `BookSlotPage` renders `null` when the `doctorProfileId` query parameter is missing (`routes/staff/ClinicToolPages.tsx:49`).

### W4b. Patient self-service fixed-time booking

Patient → `/patient/clinics/:clinicId` (hub) → `/book` → `OpenSlotList` → `GET /patients/clinics/{id}/slots` (optional `date`, `doctorProfileId`) → select a slot and appointment type → `POST /patients/clinics/{id}/slots/{slotId}/book` → `PatientBookingService.bookSlot`, which:
- checks the rejected-clinic gate
- runs `BookingProtectionService.checkAndRecordAttempt` (attempt rate limit and active-booking cap, logged to `booking_attempt_log`)
- checks the slot is OPEN and the date is not before today
- resolves the fee
- `PatientLinkingService.findOrCreatePatient`
- `Booking.bookedByPatient` `saveAndFlush`
- sets slot BOOKED

**Result:** booking ACTIVE with `booked_by_patient_account_id`.

**Broken transition (BUG-005):**
- Listing and booking compare **date only**, so earlier-today slots that are still OPEN are offered and accepted.
- Within about a minute, `NoShowDetectionService` marks such a booking NO_SHOW, because its start time is more than 10 minutes in the past.

### W4c. Queue token booking

Patient → `/patient/clinics/:id/queue-sessions` → `GET /patients/clinics/{id}/queue-sessions` (upcoming only) → `POST /patients/clinics/{id}/sessions/{sid}/queue-bookings` → `PatientQueueBookingService` → `QueueSlotService.issueNextSlot`, which creates a token slot with `token_number = MAX+1` and status BOOKED (064), then saves the booking.

Staff do the same through `POST /clinics/{id}/sessions/{sid}/queue-bookings`.

- **Result:** token slot BOOKED; booking ACTIVE.
- **Potential defect (PB-003):** the retry loop in `QueueSlotService.issueWithRetry` calls its own `@Transactional` method directly, so the proxy is bypassed. Inside the caller's transaction, the token `INSERT` is flushed later, outside the loop. Concurrent requests for the same session are therefore likely to fail instead of retrying.
- The queue-session listing filters to upcoming sessions, but the POST endpoints do not re-check the date (PB-004).

## W5. Walk-in registration (spec 063)

Operations or ClinicAdmin → `/staff/clinics/:id/walk-in` → `FrontDeskWalkInPage`:
1. **Doctor session** step (`SessionStep`, lists today's sessions, polls every 20 s, shows Doctor free/busy)
2. **Patient** step (search, or new with a phone match hint)
3. **Reason** (VisitReason enum; detail required for OTHER)

→ `POST /clinics/{id}/walk-ins` → `FrontDeskWalkInService.register`, which:
- checks the session belongs to the clinic
- authorizes Operations or ClinicAdmin
- blocks a rejected clinic
- validates the reason
- checks for a duplicate in the session unless `confirmDuplicate`
- resolves the fee
- creates or reuses the patient
- issues a slot: an untimed walk-in line place (FIXED_TIME) or a queue token (QUEUE), both BOOKED
- `Booking.walkIn(...)`
- creates an inbox item (SSE)
- computes the line position

**Result:** booking ACTIVE (source WALK_IN); slot BOOKED with no start time; inbox WALK_IN item.

The line is managed from `WalkInLinePanel` (polls every 20 s): "Send in" (→ APPEARED), "Complete", "Remove from the line" (cancel).

**Incomplete transitions:**
- The API does not require the session to be today or not yet ended (PB-004).
- A walk-in can never become a no-show (the no-show query requires a start time).
- An APPEARED untimed walk-in is never auto-completed. It "stays In with doctor until someone completes the visit" (`SlotRepository.java:227`).
- Removing a walk-in cancels the booking and resets the untimed slot to OPEN. An OPEN untimed slot is excluded from patient listings (`startTime IS NOT NULL`) but still exists as a row.

## W6. Doctor assignment

There is no separate assignment step. The doctor is fixed by the **session** the slot or token belongs to (`session.doctor_profile_id`).

- To change the doctor, staff must cancel and rebook.
- No reassign endpoint exists.
- For walk-ins, the doctor is chosen implicitly by choosing a session in `SessionStep`.

## W7. Queue management

| Step | Actor | Frontend | API | Backend | Resulting state |
|---|---|---|---|---|---|
| Issue a token | Patient or staff | queue booking forms | queue-bookings POST | `QueueSlotService` | slot BOOKED (token n) |
| See position | Patient or staff | `QueuePositionIndicator` (20 s) | `GET …/queue-position` | `QueuePositionService` counts waiting tokens ahead (064) | read-only |
| Send in | Operations or ClinicAdmin | day sheet / walk-in panel | `POST /slots/{id}/appeared` | `SlotAppearedService` (BOOKED or NO_SHOW → APPEARED; stamps `appeared_at`) | APPEARED |
| Complete | Staff or treating doctor | same | `POST /slots/{id}/complete` | `SlotCompletionService` (untimed: no start-time check) | COMPLETED; delay recalculated |
| Cancel | Staff | same | cancel / batch | `BookingCancellationService` | booking CANCELLED; slot OPEN |

- There is no "skip", "recall", "hold" or reordering action. `slot.on_hold` exists in the schema (V13) and `Slot.setOnHold` exists, but nothing in `src/main` calls it [CODE]. The column is always `false` (a dead field).
- A patient cannot self-cancel a queue booking (064, HANDOFF Part 12).

## W8. Doctor availability

ClinicAdmin or the doctor → `features/scheduling/ScheduleForm.tsx` → `POST /clinics/{id}/doctors/{dp}/schedules` → `ScheduleService`, which checks:
- the doctor is staffed at the clinic
- interval and window validity
- an optional break window
- **no overlap with the same doctor's schedules at any clinic** (backlog 010)

→ `schedule`. Nightly job (02:00, 15-day horizon) → `session`, plus `slot` rows for fixed-time sessions.

- Editing (`PATCH`) is non-retroactive: already-generated sessions keep their snapshot.
- Removing availability for a day means using session cancellation (W12) or deletion (ClinicAdmin, blocked when bookings exist).
- **Missing:** leave or holiday entries, ad-hoc sessions, and editing a single session's times.

## W9. Doctor live status

Patient (booking detail) or staff → `LiveScheduleStatusIndicator` (20 s poll) → `GET /patients/bookings/{id}/live-status` or `/clinics/{id}/sessions/{sid}/live-status` → `SessionLiveStatusService` (uses `appeared_at`/`completed_at`, `session.delay_minutes`, a clock).

- **Result:** read-only status (on time / running late / stale is hidden, per 061).
- **Dependency:** accuracy relies on staff pressing Appeared and Complete. Auto-completion (W15) fabricates completion times at slot end, which then feeds the delay.

## W10. Buffer slots

**Removed.** Backlog 022 (risk-sized buffer capacity) was implemented, then deleted by spec 058 (`V35__drop_slot_is_buffer.sql`). Its role, holding capacity for walk-ins, is now filled by the untimed walk-in line (063), which adds capacity instead of reserving it.

Leftover: a comment in `BookingRepository.java:67` still references "024-buffer-slot-capacity-sizing".

## W11. No-show handling

`NoShowDetectionTrigger` (cron `0 * * * * *`) → `NoShowDetectionService.detectAndMarkNoShows`:
- loads **all** slots with status BOOKED, `on_hold=false`, a start time, in FIXED_TIME sessions
- marks NO_SHOW when `session_date + start_time + 10 min` is before `LocalDateTime.now()`

**Result:** slot NO_SHOW; booking still ACTIVE.

Recovery: `POST /slots/{id}/appeared` accepts NO_SHOW → APPEARED (a late arrival).

**Gaps:**
- There is no staff action to mark a no-show early or to undo one other than "Appeared".
- Queue and walk-in bookings are never no-shows.
- A no-show does not free the slot for rebooking. The slot stays NO_SHOW, not OPEN, which matters for fixed-time capacity.
- No notification event is emitted.
- Uses the JVM time zone (PB-005).

## W12. Cancellation

| Variant | Actor → API | Backend | DB result | Waitlist bump | Notification event |
|---|---|---|---|---|---|
| Patient cancels | Patient → `POST /patients/bookings/{id}/cancel` (reason required; 2 h cutoff in the controller; walk-ins refused) | `BookingCancellationService` | booking CANCELLED (+ reason, `cancelled_at`); slot OPEN | yes (timed fixed-time) | none |
| Staff cancels one | Any active staff role → `POST /clinics/{id}/bookings/{id}/cancel` | same | same | yes | **none**: the patient is not notified |
| Staff batch | → `POST /sessions/{sid}/bookings/cancel-batch` | `BatchBookingCancellationService` (partial failure tolerated) | same | yes | none |
| Whole session | → `POST /sessions/{sid}/cancel` | `SessionCancellationService`: for each **BOOKED** slot, `cancelIfActive` and slot → **OPEN** | bookings CANCELLED; slots OPEN; **the session is unchanged** | no | `BOOKING_CANCELLED_SESSION` |
| Partial (from cutoff) | → `POST /sessions/{sid}/cancel-from-cutoff` | `SessionPartialCancellationService` (same slot → OPEN) | same | no | `BOOKING_CANCELLED_PARTIAL` |
| De-verification / revoke | Super Admin action → event | `DeVerificationCascadeService` (fixed-time through `BookingCancellationService`) | same | **yes** (by backlog 008) | `BOOKING_CANCELLED_DEVERIFICATION` |
| Rejection | Super Admin → event | `ClinicRejectionCascadeService` | same | no; `ClinicRejectionWaitlistListener` handles entries | `BOOKING_CANCELLED_CLINIC_REJECTED` |

**Broken transitions:**
- **BUG-002/003.** After a whole or partial session cancellation, the released slots are OPEN and pass every listing and booking filter. The "cancelled" period immediately becomes bookable again by patients and staff. Nothing records that the session, or the range, was cancelled.
- **BUG-004.** `SessionCancellationService` throws `SessionAlreadyCancelledException` whenever no slot is BOOKED. That includes a session that was simply never booked, or one whose visits are all APPEARED or NO_SHOW. There is no way to take an empty future session off the books except deletion (ClinicAdmin only).
- APPEARED or NO_SHOW visits are not cancelled by a whole-session cancellation (it filters to BOOKED only).

## W13. Rescheduling

**Missing by decision** (constitution; backlog 016/017/025). The workflow is cancel, then book again.

Consequence: between those two steps, a fixed-time slot is released and **may be offered to the waitlist** (30-minute claim window). The patient can lose their original time.

## W14. Patient check-in

"Check-in" is `POST /clinics/{id}/slots/{slotId}/appeared` (UI label "Appeared" in the day sheet, "Send in" / "In with the doctor" in the walk-in panel).

- Allowed from BOOKED or NO_SHOW, by Operations or ClinicAdmin only. Stamps `slot.appeared_at`.
- The same state is used for "arrived at the clinic" and "is with the doctor". There is no distinct *waiting (checked in)* versus *in consultation* state.
- **Incomplete transition:** clinics cannot record "arrived, waiting" separately from "in consultation", so the "Doctor busy" indicator and queue position cannot tell those two apart [INFERRED from a single APPEARED state].

## W15. Consultation lifecycle

There is no consultation entity or state machine. What exists:
1. A slot moves to APPEARED (W14).
2. The treating doctor may `POST` a consultation note (one per booking, immutable), prescriptions (one or more, immutable, with at least one item), and external record references.
   - Each is a separate route: `bookings/:id/consultation-note`, `/prescription`, `/external-record`.
3. Completion (W16).

**Gaps [CODE]:**
- Documents can be created whatever the booking or slot state: cancelled, future, or no-show (`ConsultationNoteService.create` checks tenancy and treating doctor only).
- `TreatingDoctorAuthorizationService.requireTreatingDoctor` does not check that the doctor's role at the clinic is still active (PB-008).
- There is no "consultation started" timestamp separate from `appeared_at`.

## W16. Appointment completion

- **Manual:** `POST /clinics/{id}/slots/{id}/complete`.
  - Staff may complete from BOOKED or APPEARED. The doctor may complete from APPEARED only.
  - Not allowed before the scheduled start (timed slots).
  - → COMPLETED, `completed_at`, `SessionDelayService.recalculate`.
- **Automatic:** `SlotAutoCompletionService` (every minute) completes every **timed APPEARED** slot once its end time passes.

**Issues:**
- Staff can complete a BOOKED slot, jumping check-in, so `appeared_at` stays null.
- Auto-completion records completion at the scheduled end even if the consultation is still running or never happened after check-in.
- The booking itself never changes state (it stays ACTIVE forever), so "completed appointments" can only be derived through the slot.

## W17. Administrative workflows

| Workflow | Path | Result |
|---|---|---|
| Clinic onboarding | Public `/register` → clinic plus ClinicAdmin (unverified) → Super Admin `/super-admin-console/clinics` verify → discovery-visible | `clinic.verified=true` [RUNTIME: 3 verified clinics listed] |
| Reject, restore, delete clinic | Super Admin → reject (reason) → cascade → restore or delete (guarded) → nightly purge after 30 days | `rejected=true` … |
| Doctor onboarding | ClinicAdmin → onboard Doctor → `doctor_profile` in the licence queue → Super Admin verifies → set appointment types and fee (`/doctors/:id/appointment-types`) → define schedule → sessions generated | "Booking setup incomplete" warning on the Doctors page when fee or types are missing (`GET /doctors/booking-readiness`) |
| Staff lifecycle | onboard → (reset password) → deactivate with reason; last ClinicAdmin protected | no reactivate |
| Protection | Super Admin edits global settings (audited); ClinicAdmin resolves flags and sets a per-clinic cap override (audited) | |
| Session generation | Nightly, or Super Admin manual trigger | |
| Retention / anonymization | ClinicAdmin anonymizes a patient; monthly purge | |

---

## Summary of broken or incomplete transitions

| Transition | Where | Ref |
|---|---|---|
| Cancelled session or range → slots become bookable again | W12 | BUG-002, BUG-003 |
| Cancel an unbooked session → error "already cancelled" | W12 | BUG-004 |
| Elapsed OPEN slot today → offered and bookable → instant no-show | W4b, W11 | BUG-005 |
| New-patient phone collision → wrong error or 500 | W3 | PB-001, PB-002 |
| Concurrent token issuance → failure instead of retry | W4c, W5 | PB-003 |
| Staff, queue and walk-in bookings on past or ended sessions accepted by the API | W4a, W4c, W5 | PB-004 |
| Clinical documents on cancelled or future bookings; by an ex-staff doctor | W15 | PB-008 |
| Check-in and "with doctor" share one state; completion can skip check-in; auto-complete fabricates end | W14, W16 | UX-05, UX-06 |
| Staff cancellation → patient not notified | W12 | UX-12 |
| Reschedule = cancel + rebook, exposing the slot to the waitlist | W13 | UX-09 |
