# Feature Specification: Send-In and Complete for Queue Sessions

**Feature Branch**: `064-queue-send-in-complete`

**Created**: 2026-09-24

**Status**: Draft

**Input**: User description: "Queue sessions get the same send-in / complete flow as appointment sessions. Today a booked queue token stays open and queue sessions have no 'In with doctor' or 'Complete' step, so every real queue patient shows position 1, and even correct counting would never go down because nobody is ever marked seen. Remedy (option B): mark a queue token booked when booked; make the existing 'In with doctor' and 'Complete' actions available for queue sessions, recording sent-in and finished times; queue position counts only tokens still waiting ahead; Day Sheet Send in / Complete on queue rows; the front-desk waiting-line panel and Doctor free/busy hint work for queue sessions; migrate existing active queue bookings. No second queue or status mechanism, no 'now serving' counter."

## Background

Two separate gaps make queue (token) sessions misreport a patient's place in line:

1. **Nothing is counted.** Queue position counts the booked tokens ahead of a patient, but booking a token never marks it booked, so no patient ever has anyone "ahead". Every real queue patient sees position 1.
2. **Nothing ever moves.** Queue sessions have no "In with doctor" or "Complete" step. Even with correct counting, the system would never learn that a patient ahead had been seen, so a position could only stay the same or grow.

Appointment (Fixed-Time) sessions already have both steps, and the front-desk walk-in line (063) uses the same untimed-slot shape queue tokens use. This feature gives queue sessions the same steps. It is recorded as the product owner's choice ("option B", 2026-09-24) over a count-only patch or a separate "now serving" counter.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Staff send queue patients in and complete their visits (Priority: P1)

As front-desk staff running a token queue, I want to mark each token "In with doctor" when I send the patient in and "Complete" when the visit ends, just as I do for appointment patients, so the queue reflects who has been seen.

**Why this priority**: Without this step nothing in a queue session ever changes state, so no other improvement (position, hints) can work.

**Independent Test**: In a queue session with three booked tokens, mark token 1 In with doctor, then Complete, from the Day Sheet. Confirm the statuses change, the sent-in and finished times are recorded, and tokens 2 and 3 are unaffected.

**Acceptance Scenarios**:

1. **Given** a booked token in a queue session, **When** staff mark it In with doctor, **Then** its status becomes In with doctor and the time it went in is recorded.
2. **Given** a token that is In with doctor, **When** staff or the treating doctor mark it Complete, **Then** its status becomes Completed and the finish time is recorded.
3. **Given** a queue session's Day Sheet, **When** staff view a waiting token's row, **Then** the Send in action is available; **Given** a token In with doctor, **Then** Complete is available.
4. **Given** several waiting tokens, **When** staff send in a token out of number order (for example, an urgent patient), **Then** it is allowed.
5. **Given** a Doctor caller, **When** they view the Day Sheet, **Then** Send in stays hidden for them, as for appointment sessions, and Complete is available for their own session.

---

### User Story 2 - Patients and staff see a true, shrinking queue position (Priority: P1)

As a patient waiting in a token queue, I want my position to show how many patients are genuinely still ahead of me and to go down as they are seen, so I know how long I'll wait.

**Why this priority**: This is the reported defect; a position stuck at 1 misleads every queue patient.

**Independent Test**: Book tokens 1–4; confirm token 4 shows position 4. Send in token 1 → token 4 shows 3. Complete token 1 and send in token 2 → token 4 shows 2. Cancel token 3 → token 4 shows 1.

**Acceptance Scenarios**:

1. **Given** four waiting tokens, **When** the patient with token 4 views their position, **Then** it is 4.
2. **Given** that queue, **When** token 1 is sent in, **Then** token 4's position becomes 3 (a patient who is In with doctor or Completed no longer counts as ahead).
3. **Given** a cancelled token ahead, **When** positions are shown, **Then** it no longer counts as ahead.
4. **Given** a patient whose own token is In with doctor or Completed, **When** they view their position, **Then** no position is shown (they are no longer waiting), as today for finished visits.
5. **Given** the staff queue-position view, **When** staff look up a booking, **Then** they see the same number the patient sees.

---

### User Story 3 - The front-desk screen works for queue sessions too (Priority: P2)

As front-desk staff on the Walk-in screen (063), I want a queue session to show its waiting line, whether the doctor is free or busy, and Send in / Complete / Remove, just like an appointment session's walk-in line.

**Why this priority**: 063 had to limit these to appointment sessions only because queue sessions lacked the steps this feature adds.

**Independent Test**: Choose a queue session on the Walk-in screen. Confirm the waiting line lists every waiting token (booked and walk-in) in number order, the Doctor free/busy hint follows who is In with doctor, and Send in / Complete / Remove work.

**Acceptance Scenarios**:

1. **Given** a queue session is chosen on the Walk-in screen, **When** the side panel loads, **Then** it lists every waiting token in number order, marking walk-ins, with the first one suggested next.
2. **Given** a queue session, **When** a token is In with doctor, **Then** the session shows "Doctor busy" on the Walk-in screen's doctor list and panel; otherwise "Doctor free now".
3. **Given** the Walk-in screen's doctor list, **When** a queue session is shown, **Then** it shows how many patients are waiting.

---

### User Story 4 - Existing queue bookings are carried over (Priority: P3)

As the clinic, I want queue bookings made before this change to behave the same as new ones, so nobody's token is left in the old state.

**Why this priority**: Very little existing data is affected (two active queue bookings in development today), but no booking should be left behind.

**Independent Test**: With an active queue booking made before the change, apply the change and confirm the token now counts as waiting, shows a correct position, and can be sent in.

**Acceptance Scenarios**:

1. **Given** an active queue booking whose token is still in the old open state, **When** the change is applied, **Then** the token counts as waiting.
2. **Given** a cancelled queue booking's token, **When** the change is applied, **Then** it is left as it is.

---

### Edge Cases

- **Cancelling a waiting token** frees it exactly as today: it is never re-issued (token numbers are never reused) and never offered to the waitlist.
- **A token from a past day never sent in**: it stays waiting and is never counted against anyone, because positions are per session. It does not block the patient's later anonymization and is not swept up as a future booking (the 2026-09-24 today-or-later rule for untimed slots).
- **Automatic no-show and auto-completion**: they keep skipping queue tokens, which have no scheduled time. Staff complete or remove tokens by hand.
- **Patient self-cancel**: unchanged. Patients still can't self-cancel a queue booking.
- **Live schedule status (061)**: stays not-applicable for queue sessions, which have no schedule to run early or late against.
- **Two tokens In with doctor at once**: allowed, as for appointment sessions. The hint says "Doctor busy" while any token is In with doctor.
- **Rejected clinic (062)**: unchanged. Queue sessions at a rejected clinic take no bookings, and only the ClinicAdmin keeps access.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Every path that books a queue token — patient self-service, staff queue booking, and the front-desk walk-in (063) — MUST mark the token as booked (waiting) at the moment of booking.
- **FR-002**: Staff MUST be able to mark a waiting queue token In with doctor, with the same roles and rules as for appointment sessions. The time it went in MUST be recorded.
- **FR-003**: Staff or the treating doctor MUST be able to mark a queue token Complete, with the same roles and rules as for appointment sessions. The finish time MUST be recorded. The appointment-only rule "can't complete before the slot's start time" does not apply to untimed tokens.
- **FR-004**: A patient's queue position MUST equal the number of the same session's tokens that are still waiting with a lower token number, plus one. In with doctor, Completed and cancelled tokens MUST NOT count.
- **FR-005**: A patient whose own token is In with doctor, Completed or cancelled MUST NOT be shown a queue position.
- **FR-006**: The Day Sheet MUST offer Send in for waiting queue tokens and Complete for In-with-doctor queue tokens, with the same role visibility as for appointment sessions.
- **FR-007**: The front-desk Walk-in screen (063) MUST, for a queue session, show its waiting line (every waiting token in number order, walk-ins marked), the Doctor free/busy hint, the number of patients waiting, and Send in / Complete / Remove.
- **FR-008**: This feature MUST NOT add a second queue, status or "now serving" mechanism. It reuses the existing token numbering, booking states and In with doctor / Complete actions.
- **FR-009**: Cancelling a queue booking MUST still free its token without re-issuing its number and without a waitlist offer.
- **FR-010**: Automatic no-show detection and auto-completion MUST continue to skip queue tokens.
- **FR-011**: Existing active queue bookings whose tokens are still in the old open state MUST be migrated to waiting, once. Cancelled and completed bookings are left untouched.
- **FR-012**: All new reads and writes MUST be scoped to the requesting clinic.

### Key Entities

- **Queue token**: an existing untimed slot in a queue session with a token number. Its states are now waiting (booked), In with doctor, and Completed, the same states appointment slots use, plus open once freed by a cancellation.
- **Visit times**: the sent-in and finished times added in 063, now recorded for queue tokens too.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In a queue of N waiting patients, the patient with the last token sees position N, not 1.
- **SC-002**: Each time a patient ahead is sent in or cancelled, every later patient's position goes down by exactly one on their next view.
- **SC-003**: 100% of queue patients who are seen have both a sent-in and a finished time recorded.
- **SC-004**: No queue token is ever offered to the waitlist, auto-marked no-show or auto-completed.
- **SC-005**: Appointment-session behavior is unchanged: all existing automated tests for appointment booking, send-in, completion and position pass.

## Assumptions

- **Roles** mirror appointment sessions exactly: Operations and ClinicAdmin send patients in; staff or the treating doctor complete.
- **Out-of-order send-in** is allowed (for example, for urgent patients), as for appointments. There is no reordering feature.
- **Front-desk "waiting" count** for a queue session counts every waiting token, booked or walk-in, since both wait in the same line. This supersedes 063's "0 for queue sessions" rule, which existed only because queue sessions lacked these steps.
- **Migration scope**: a one-time update of active queue bookings' open tokens. There are two such rows in development today, both old Star Clinic test bookings.

## Out of Scope

- Automatic no-show for queue tokens.
- Token display screens or "now serving" boards.
- Reordering tokens.
- Notifications to patients when their position changes.
- Changes to appointment-session behavior.
