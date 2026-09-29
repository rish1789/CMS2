# 06 — UI / UX Audit

Labels: [RUNTIME] = observed while running · [CODE] = read in source · [INFERRED] = drawn from code or docs, not proven · [UNKNOWN] = could not be determined.

## How this was assessed

- **Viewed [RUNTIME]:** the public pages in the in-app browser: landing, `/discover` (search with filters, 3 results) and `/staff/login`.
- **Read in source [CODE]:** staff and patient screens. No valid staff or patient credentials were available (see 00 §16).
- **Not assessed:** visual design quality of authenticated screens, and real responsive behaviour. The latter is recorded as [UNKNOWN] beyond the code evidence that spec 055 (responsive pass) and the `SidebarDrawer` exist.
- **Prior context:** memory notes that the user found earlier UI "sloppy (space/placement)" and the copy "childish" (backlog 053, since marked Converged). This audit does not re-judge aesthetics; it focuses on workflow and correctness.

## Persona walkthroughs

### Receptionist (Operations)

Main surfaces:
- Walk-in page (`features/front-desk-walk-in`)
- Day sheet (`features/day-sheet`)
- Staff booking modals
- Patient search
- Inbox

**Strengths [CODE]:**
- The walk-in flow is modelled as a clinic task, not a CRUD action. It has three steps: doctor session (with "Doctor free now / Doctor busy"), patient (search or new, with a phone-match hint), and reason (enum with an "Other" detail).
- A duplicate-in-session confirmation exists.
- The waiting line has "Send in", "Complete" and "Remove from the line" actions.

**Friction:**
- Status changes need explicit clicks for every patient: "Appeared", then "Complete".
- Live panels refresh every 20 s, so "Doctor free/busy" can be up to 20 s stale.
- Reschedule means cancel, then book again.

### Doctor

- Sees the day sheet for their sessions.
- Can complete APPEARED visits.
- Writes documents on three separate pages per booking: `bookings/:id/consultation-note`, `/prescription`, `/external-record` (`App.tsx:143-145`).
- There is no "my consultation" view that combines notes, prescriptions and the previous history of the patient in one place, apart from `PatientHubPage`.

### Administrator (ClinicAdmin)

Staff onboarding, deactivation and password reset; schedules; appointment types and fees; the "Booking setup incomplete" warning; protection flags and limits; anonymization.

- ClinicAdmin inherits every Operations surface.
- Deletion of sessions and schedules is safety-gated.

### Super Admin

- Clinic and doctor verification lists (`PendingClinicsList.tsx` 770 lines, `PendingDoctorsList.tsx` 849 lines): tabs, bulk actions, reject reasons.
- Protection settings.
- Signs in through the **"Clinic sign in"** screen with an **"Email or Staff Code"** label [RUNTIME screenshot].

### Patient

- Discovery (public), clinic hub, fixed-time or queue booking, my bookings (with a waitlist tab), queue position and live status, clinical records.
- Discovery results link to `/patient/clinics/{clinicId}?doctorId=…` (`DiscoverySearch.tsx:173`), which requires login.

---

## Issue register (UX-01 … UX-22)

| ID | Area | Issue | Evidence | Effect on the clinic workflow |
|---|---|---|---|---|
| UX-01 | Terminology | The same visit state has several names: "Appeared" (day sheet `STATUS_LABEL`), "Send in" / "In with the doctor" (walk-in panel), "Completed" vs "Session complete". The database term "Appeared" is shown directly to staff. | `features/day-sheet/SessionSlotsView.tsx:9-15`; walk-in copy | Staff must learn that "Appeared" means both "checked in" and "with doctor" |
| UX-02 | Terminology | System concepts surface as user language: "Session", "Slot", "Token", "Queue sessions" (patient route `/queue-sessions`), "Walk-in line", "Appointment type". A slot label is `HH:MM–HH:MM`, `Token n`, or the literal fallback **"Slot"**. | `SessionSlotsView.tsx:3-7`, `App.tsx:117` | Patients especially see scheduling internals |
| UX-03 | Cancellation | After "Cancel session" or "Cancel from cutoff", the day sheet shows the released slots as **Open** with no indication the session or range was cancelled. They are re-bookable. | BUG-002/003; no session status | A receptionist or patient can book into a period the doctor is absent |
| UX-04 | No-show | No manual "Mark no-show" and no undo besides "Appeared". Walk-ins and queue tokens can never be no-shows. | `SlotStatus` transitions; `NoShowDetectionService` | The receptionist cannot clear a line of absent walk-ins except by cancelling |
| UX-05 | Check-in model | One APPEARED state conflates "arrived and waiting" with "in consultation" | `SlotAppearedService` | Queue and "Doctor busy" indicators cannot tell a waiting room from a consultation room |
| UX-06 | Completion | Staff can complete a BOOKED visit (skipping check-in). Timed APPEARED visits are auto-completed at slot end. | `SlotCompletionService`, `SlotAutoCompletionService` | "Completed" does not reliably mean seen; delay and live status are derived from these times |
| UX-07 | Doctor workflow | Clinical documentation is split across 3 routes per visit | `App.tsx:143-145` | Multi-page hopping during consultation |
| UX-08 | Consistency | Booking and cancellation exist both as modals and as full-page routes (`slots/:slotId/book`, `bookings/:bookingId/cancel`). `BookSlotPage` renders **nothing** if `?doctorProfileId` is missing. | `App.tsx:136-141`, `ClinicToolPages.tsx:49` | Inconsistent back and escape behaviour; possible blank page from stale links |
| UX-09 | Reschedule | No reschedule. Cancel + rebook, during which the waitlist may take the slot. | constitution; W13 | Patients can lose their time; two actions for one intent |
| UX-10 | Live updates | 20 s polling in 4 places, SSE only for the inbox | `QueuePositionIndicator.tsx:54`, `LiveScheduleStatusIndicator.tsx:74`, `SessionStep.tsx:92`, `WalkInLinePanel.tsx:59` | Stale positions and statuses; duplicated polling code |
| UX-11 | Session expiry | Guards check only that a token exists; an expired token (12 h) produces per-screen error messages rather than a sign-in redirect. Only 7 files reference status 401. | `routes/guards.tsx`, `lib/apiClient.ts` | End-of-shift confusion for long-running front-desk tabs |
| UX-12 | Communication | Patients receive no event when staff cancel their booking, when their booking is confirmed, or when the doctor is delayed. Delivery is a log stub. There are no preferences. | 02 #42 | Patients learn about changes only by opening the app |
| UX-13 | Dead end | Patient "Forgot password" shows "contact your clinic for help", but no clinic tool can reset a patient account | `features/patient-account/LoginForm.tsx:67-69`; no endpoint | A locked-out patient has no recovery path |
| UX-14 | Onboarding | A staff account with no active clinic can sign in and lands on an empty clinic list | [RUNTIME] login 200, `/clinics/mine` → `{"clinics":[]}` | Unexplained empty state for deactivated staff |
| UX-15 | Waitlist | De-verification cascades offer the freed slots of the de-verified doctor or clinic to waitlisted patients (backlog 008 design) | `DeVerificationCascadeService.cancelOne` → `BookingCancellationService` → `WaitlistBumpListener` | Patients are invited to a provider that was just de-verified |
| UX-16 | Patient booking | Elapsed slots earlier today are offered | BUG-005 | The patient books a time already gone and is marked no-show |
| UX-17 | Walk-in | The duplicate-phone warning is advisory. Proceeding with "new patient" on a matching phone fails server-side without a specific message. | `PatientStep.tsx:22-30`, PB-002 | Front desk sees a generic error at the last step |
| UX-18 | Day sheet | The main operational table paginates at 25 rows (`SLOTS_PAGE_SIZE = 25`) | `SessionSlotsView.tsx:1` | Busy sessions need paging mid-clinic |
| UX-19 | Error copy | Generic, non-actionable messages in several flows: "Something went wrong. Please try again.", "Failed to load this session.", "Couldn't send the patient in." | walk-in and day-sheet sources | No recovery guidance |
| UX-20 | Sign-in | Super Admin uses the clinic staff screen ("Clinic sign in", "Email or Staff Code") | [RUNTIME] screenshot; `StaffAuthService` | Role confusion |
| UX-21 | Time | Times are rendered in browser-local format. "Has the slot started" is computed in the browser (`hasSlotStarted`) while the server uses its JVM zone. There is no clinic time zone. | `SessionSlotsView.tsx:30-36` | Button availability can disagree with the server when device or server clocks or zones differ |
| UX-22 | Accessibility (static) | The oxlint jsx-a11y rules pass, and components use `aria-label`, `sr-only` and breadcrumb `nav`. **Not verified** with keyboard-only or screen-reader runs. | [RUNTIME lint], [UNKNOWN runtime a11y] | Needs a manual audit |

## Loading, empty and confirmation states [CODE, heuristic]

- **Shared primitives exist:** `LoadingState`, `ListSkeleton`, `EmptyState`, `Toast`, `DeleteConfirmModal`, `RejectConfirmModal`, `ResetPasswordResultModal`.
- **Lists:** most use them (day-sheet, patient-search, clinic/doctor verification, protection, staff-picker).
- **Forms:** typically show "Registering…"-style submit text.
- **Without any loading or empty text (grep):** booking-cancellation, session-cancellation, staff-booking, staff-onboarding, patient-account, session-generation. These are mostly forms.
- **Confirmations exist** for removing a walk-in ("Remove from the line?"), delete and reject flows, and duplicate walk-ins.

## Places where the UI represents a database operation rather than a clinic task

1. "Appeared" / "Complete" buttons: status writes on a slot, not "Check in", "Call in" or "Finish consultation".
2. "Cancel session" / "Cancel from cutoff": reopens capacity instead of recording "Doctor unavailable".
3. Consultation note, prescription and external record as separate create pages: rows in three tables rather than one consultation.
4. Schedules: weekly recurrence rows. Real availability exceptions (leave, holiday, a late start today) are not expressible except by cancellation or deletion.
5. Patient creation happens only as a side effect of booking; there is no "Register patient" task.

The walk-in flow (063) is the counter-example. It is task-shaped: who, which doctor, why.
