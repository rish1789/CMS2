# Feature Specification: Front-Desk Walk-In Registration with a Walk-In Line

**Feature Branch**: `063-front-desk-walk-in`

**Created**: 2026-09-24

**Status**: Draft

**Input**: User description: "A new clinic-level front-desk Walk-in screen replaces the session-first walk-in flow: identify or register the patient (new patient = name, phone, optional email) → required visit reason (standard list, 'Other' → free text) → choose a doctor from today's sessions with their live status and load → register. In a Fixed-Time session the walk-in takes no time slot: they join that session's walk-in line (W1, W2…), reusing the existing token mechanism. Staff press 'Send in' when the doctor is free, guided by a 'Doctor free now' hint from the existing status. The system records when the walk-in went in and when the visit finished. In Queue sessions a walk-in takes the next token in the same line, marked as a walk-in. The old walk-in slotting (no-show-slot replacement, open slot with override reason) is removed from the walk-in flow."

## Product Decisions (2026-09-24, from the product owner)

These were settled in conversation before this spec was written and are recorded here as explicit decisions:

1. **Front-desk screen**: walk-in registration moves to a new clinic-level screen that starts from the patient, not from a doctor's session.
2. **Visit reason**: required. Picked from a standard list (Fever / Cold & cough, Pain, Follow-up visit, Test / report review, Prescription refill, Injury, General check-up, Other). "Other" requires a short free-text reason.
3. **New patient details**: name, phone (optional, as today), and email if they have one. No age/DOB/sex.
4. **Walk-in model**: in a Fixed-Time session, a walk-in does **not** take a timed appointment slot. They wait in the session's walk-in line and are sent in between booked appointments when the doctor is free. The system records when they went in and when the visit finished.
5. **Old slotting removed**: walk-ins no longer replace a no-show's slot or take an open timed slot with an override reason. **This overrides the walk-in insertion rules in backlog 025 and feature 058.**
6. **"Doctor free now" hint**: shown from the existing in-with-doctor/complete status. Staff still decide when to send someone in.
7. **Queue (token) sessions**: a walk-in takes the next token in the same line as booked patients, now marked as a walk-in.

## Clarifications

### Plan-time scope clarifications (2026-09-24)

Found while researching the code for the plan (research.md Decisions 2 and 7). Recorded here for the product owner's review:

- **Send in / "Doctor free now" / visit times apply to Fixed-Time sessions.** The app's existing in-with-doctor and complete actions exist only for Fixed-Time sessions; Queue sessions have no send-in or complete step at all today. Adding one would change how booked queue patients are handled, which is out of scope. Queue walk-ins take the next token, as decided, and are served like every other token.
- **Position in line is shown for Fixed-Time walk-ins.** For Queue sessions the confirmation shows the token number only: the existing queue-position calculation doesn't work for real queue bookings (a pre-existing bug, flagged separately, not changed here).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Register a walk-in from one front-desk screen (Priority: P1)

As front-desk staff, when a patient walks in I want to identify or register them, record why they came, pick a doctor who is working today, and add them to that doctor's line — all from one screen, without first hunting for a doctor's session.

**Why this priority**: This is the core workflow the clinic runs many times a day; today it forces staff to navigate Day Sheet → doctor → session before they can even look up the patient.

**Independent Test**: Open the front-desk Walk-in screen, register a new patient with a visit reason, pick a doctor with a Fixed-Time session today, and confirm the patient appears in that session's walk-in line with a W-number, the fee locked, and the confirmation showing the doctor, the W-number and how many walk-ins are ahead.

**Acceptance Scenarios**:

1. **Given** an existing patient of this clinic, **When** staff search by name or phone and select them, **Then** the patient is used without re-entering details.
2. **Given** a new patient, **When** staff enter a name (and optionally phone and email), **Then** a new clinic patient record is created at registration; a phone that matches an existing patient of this clinic is flagged so staff can pick that record instead.
3. **Given** the visit-reason step, **When** staff pick a reason from the list, **Then** it is recorded on the visit; **When** they pick "Other", **Then** a short text reason is required before continuing.
4. **Given** the doctor step, **When** staff view the options, **Then** they see every doctor session at this clinic today that is still running or yet to start, each with the doctor's name, session time, session mode, the existing live schedule status, how many patients are booked, and how many walk-ins are waiting.
5. **Given** a Fixed-Time session is chosen, **When** staff register the walk-in, **Then** the patient joins that session's walk-in line with the next W-number, takes no timed slot, and every booked appointment in that session is unchanged.
6. **Given** a Queue (token) session is chosen, **When** staff register the walk-in, **Then** the patient takes the next token in the same line as booked patients and is marked as a walk-in.
7. **Given** registration succeeds, **When** the confirmation appears, **Then** it shows the patient, doctor, W-number (with position in line) or token number, and locked fee.
8. **Given** the chosen doctor's booking setup is incomplete (no appointment type or fee), **When** staff reach the doctor step, **Then** that session is shown as not available for walk-ins with the reason, instead of failing at submit.

---

### User Story 2 - Send a waiting walk-in in when the doctor is free (Priority: P1)

As front-desk staff, I want to see each doctor's walk-in line and whether the doctor is free right now, and send the next walk-in in between booked appointments, so walk-ins are seen without disturbing booked patients.

**Why this priority**: Registering a walk-in is only half the workflow; without a way to send them in, the line has no purpose.

**Independent Test**: With two walk-ins waiting for a Fixed-Time session and nobody currently in with the doctor, confirm the screen shows "Doctor free now", press "Send in" on W1, and confirm W1 is marked as in with the doctor with the time recorded, the hint changes to "Doctor busy", and W2 moves to the front.

**Acceptance Scenarios**:

1. **Given** a session with nobody currently in with the doctor, **When** staff view its walk-in line, **Then** a "Doctor free now" hint is shown; **Given** a patient is currently in with the doctor, **Then** a "Doctor busy" hint is shown instead.
2. **Given** a waiting walk-in, **When** staff press "Send in", **Then** the walk-in is marked as in with the doctor and the time they went in is recorded.
3. **Given** a walk-in who is in with the doctor, **When** staff (or the doctor) mark the visit complete, **Then** the time the visit finished is recorded.
4. **Given** several walk-ins are waiting, **When** staff view the line, **Then** they are shown in arrival order, with the first one suggested next; staff may still send in a different one when needed.
5. **Given** the hint says "Doctor busy", **When** staff press "Send in" anyway, **Then** the action is allowed — the hint guides, it does not block.

---

### User Story 3 - Walk-ins are recognisable everywhere they appear (Priority: P2)

As clinic staff, I want every walk-in — whether in a Fixed-Time walk-in line or in a token queue — to be marked as a walk-in with its visit reason, so the Day Sheet, staff inbox and patient history tell walk-ins apart from booked visits.

**Why this priority**: Today queue-session walk-ins are indistinguishable from booked tokens; the clinic loses that information.

**Independent Test**: Register one walk-in into a Fixed-Time session and one into a Queue session; confirm both show a walk-in badge and their visit reason on the Day Sheet and both create the staff inbox walk-in notice.

**Acceptance Scenarios**:

1. **Given** a walk-in registered into any session mode, **When** staff view the Day Sheet, **Then** the visit is shown with a walk-in badge and its visit reason.
2. **Given** a walk-in registered into any session mode, **When** registration completes, **Then** the existing staff inbox walk-in notice is created.
3. **Given** a Fixed-Time session's Day Sheet, **When** walk-ins are waiting, **Then** the walk-in line is shown alongside (not mixed into) the timed appointment list.

---

### User Story 4 - Start a walk-in from a doctor's Day Sheet (Priority: P3)

As front-desk staff already looking at a doctor's Day Sheet, I want the existing walk-in button to open the new front-desk screen with that session already chosen, so I don't lose my place.

**Why this priority**: Convenience; the front-desk screen alone covers the workflow.

**Independent Test**: From a Fixed-Time session's Day Sheet and from a Queue session's Day Sheet, press the walk-in button and confirm the front-desk screen opens with that session pre-selected.

**Acceptance Scenarios**:

1. **Given** a session's Day Sheet, **When** staff press its walk-in button, **Then** the front-desk Walk-in screen opens with that session pre-selected at the doctor step.

---

### Edge Cases

- **Same patient registered twice**: if the patient is already waiting in, or already booked into, the chosen session, staff are warned and must confirm before a second registration.
- **Session not yet started**: walk-ins may join the line of a session that starts later today; they wait until it runs.
- **Session over**: a session whose scheduled end has passed is not offered unless someone is still in with the doctor or waiting in its line.
- **Walk-in leaves without being seen**: staff can remove a waiting walk-in from the line; it is recorded as cancelled and does not count as a no-show.
- **Walk-ins still waiting at the end of the day**: they stay visible as not seen; nothing is cancelled automatically, and they never block the patient's later anonymization (per the 2026-09-24 rule that past unseen queue tokens don't block).
- **Doctor free but a booked appointment is due**: the hint only reflects whether someone is in with the doctor now; staff judge whether a walk-in fits before the next appointment.
- **Live schedule status**: walk-ins have no scheduled time, so they never count as "running early" or "delayed" in the existing live schedule status; they only affect the "Doctor free now/busy" hint.
- **Rejected clinic**: the existing rejected-clinic refusal (062) applies to walk-in registration unchanged.
- **Fee not configured**: the existing no-fee refusal applies, but is surfaced at the doctor step (US1 Scenario 8).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide a clinic-level front-desk Walk-in screen, available to the same staff roles that can register walk-ins today (Operations and ClinicAdmin), that runs the steps: patient → visit reason → doctor session → confirm.
- **FR-002**: Patient step MUST reuse the existing clinic patient search for existing patients. For a new patient it MUST collect name (required), phone (optional, as today; validated as an Indian mobile number when given), and email (optional, validated as an email address when given).
- **FR-003**: When a new patient's phone is given and matches an existing patient of the same clinic, the system MUST show the match and let staff use it instead of creating a duplicate.
- **FR-004**: A visit reason MUST be required for every walk-in: one of Fever / Cold & cough, Pain, Follow-up visit, Test / report review, Prescription refill, Injury, General check-up, or Other. "Other" MUST require free text (maximum 200 characters). The reason MUST be stored on the visit.
- **FR-005**: The appointment type (which determines the locked fee) MUST still be chosen, from the selected doctor's appointment types, and the fee MUST be locked at registration exactly as today.
- **FR-006**: The doctor step MUST list this clinic's sessions for today that are running or yet to start (FR edge case "Session over"), each showing doctor, session time, mode, the existing live schedule status, booked count, and walk-ins waiting. It MUST NOT introduce any new doctor availability or status logic.
- **FR-007**: A session whose doctor's booking setup is incomplete MUST be shown as unavailable for walk-ins, with the reason, using the existing booking-readiness information.
- **FR-008**: Registering into a Fixed-Time session MUST add the patient to that session's walk-in line with the next walk-in number (W1, W2, …), MUST NOT occupy or alter any timed slot, and MUST NOT affect any booked appointment.
- **FR-009**: Registering into a Queue session MUST issue the next token in the same line as booked patients.
- **FR-010**: Every walk-in, in either mode, MUST be marked as a walk-in and MUST create the existing staff inbox walk-in notice.
- **FR-011**: The walk-in line and token queue MUST reuse the existing queue/token mechanism; the system MUST NOT create a second queue system.
- **FR-012**: The system MUST show, per Fixed-Time session, a "Doctor free now" hint when no patient in that session is currently in with the doctor, and "Doctor busy" otherwise, derived from the existing in-with-doctor/complete status.
- **FR-013**: Staff MUST be able to "Send in" any waiting walk-in; this uses the existing in-with-doctor action and is never blocked by the hint.
- **FR-014**: The system MUST record the time a patient was sent in and the time their visit was completed.
- **FR-015**: Staff MUST be able to remove a waiting walk-in from the line; it is recorded as cancelled, not as a no-show.
- **FR-016**: The previous walk-in slot insertion (placing a walk-in into a no-show's slot or into an open timed slot with an override reason) MUST no longer be used by the walk-in flow. Existing records, including stored override reasons, MUST be left as they are.
- **FR-017**: Before registering a patient who is already waiting in, or booked into, the chosen session, the system MUST warn staff and require confirmation.
- **FR-018**: The confirmation MUST show the patient, doctor, walk-in number with position in line (Fixed-Time) or token number (Queue), and locked fee.
- **FR-019**: The Day Sheet MUST show walk-ins with a walk-in badge and visit reason, and for Fixed-Time sessions MUST show the walk-in line separately from the timed appointments.
- **FR-020**: The existing per-session walk-in buttons on the Day Sheet MUST open the front-desk screen with that session pre-selected.
- **FR-021**: All new reads and writes MUST be scoped to the requesting clinic.

### Key Entities

- **Walk-in visit**: a booking marked as a walk-in, with its visit reason, the walk-in number or token, the time sent in, and the time completed. It belongs to one doctor session at one clinic.
- **Walk-in line**: the ordered set of waiting walk-ins for one Fixed-Time session, numbered W1, W2… in arrival order. It uses the same mechanism as token queues.
- **Visit reason**: one of the fixed list values, or "Other" with free text.
- **Clinic patient record**: existing (name, phone), gaining an optional email.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Front-desk staff can register an existing patient as a walk-in in under 60 seconds, and a new patient in under 90 seconds, without leaving the Walk-in screen.
- **SC-002**: 100% of walk-ins, in both session modes, carry a visit reason and are marked as walk-ins.
- **SC-003**: Registering a walk-in into a Fixed-Time session changes 0 booked appointments and 0 timed slots.
- **SC-004**: For every walk-in who was seen, the time they went in and the time their visit finished are recorded.
- **SC-005**: No walk-in registration fails at submit because of incomplete doctor setup; such sessions are identified before staff choose them.
- **SC-006**: Existing booking, queue, cancellation and live-status behaviour for booked appointments is unchanged (all existing automated tests pass).

## Assumptions

- **Send-in and completion times** are recorded by the same in-with-doctor and complete actions that booked visits already use, so they are recorded for every visit, not only walk-ins. This is the simplest consistent rule and adds no new action.
- **Existing actions are reused**: "Send in" is the existing in-with-doctor action; completion is the existing complete action; removing a walk-in is the existing staff cancellation. The plan will confirm each works for a walk-in without a scheduled time, and adjust where it doesn't.
- **Arrival order**: the walk-in line is first-come, first-served by registration time. Staff may send in out of order (e.g. an urgent case); no separate priority field is added.
- **Email** is contact information on the clinic patient record only. It is not a login and does not link to a patient account.
- **Permissions** are unchanged: Operations and ClinicAdmin register and send in walk-ins; Doctors keep their existing complete action.
- **The old walk-in page** (session-first form with the override reason) is retired and replaced by the front-desk screen; its URL redirects to the new screen with the session pre-selected.
- **No-show handling** for booked appointments is unchanged. Walk-ins in a Fixed-Time line have no scheduled time, so the automatic no-show sweep never touches them.

## Out of Scope

- Payments, file uploads, age/DOB/sex capture.
- Any new doctor availability, presence or leave model.
- Changes to how booked appointments are made, moved or cancelled.
- Automatic scheduling of walk-ins into gaps (staff decide when to send in).
- Retention or cleanup rules for no-show records (not needed: walk-ins no longer delete them).
