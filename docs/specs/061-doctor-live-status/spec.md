# Feature Specification: Doctor Live Schedule Status

**Feature Branch**: `061-doctor-live-status`

**Created**: 2026-09-23

**Status**: Draft

**Input**: User description: "Enhance the existing Doctor Live Update system (Session Delay Tracking) with a reliable live schedule/status calculation, conceptually similar to live train tracking — ON_TIME / RUNNING_EARLY / DELAYED / X minutes early / X minutes late, a clinic operational day starting 04:30 AM, the doctor's actual scheduled first slot as the reference point, comparison of scheduled vs. actual progression through the real appointment lifecycle, a continuously-updating live counter reusing the existing real-time mechanism, and both an enhanced doctor/admin view and a new patient-facing view — without rebuilding the existing feature."

## Clarifications

### Session 2026-09-23

- Q: Should the new 04:30 AM operational-day boundary apply only to this live-status feature, or also change existing features' (nightly session generation, no-show sweep, Day Sheet date grouping) "today" determination? → A: Scope it to this feature only — no change to any existing feature's date handling.
- Q: For a doctor with more than one Fixed-Time session on the same operational day, should live status be tracked independently per session, or merged into one doctor-wide status? → A: Independent per session — no cross-session merging.
- Q: What polling interval should the live status views refresh on? → A: Reuse `QueuePositionIndicator`'s existing ~20-second interval.

### Session 2026-09-24

- Q: A Fixed-Time session from a past operational day that still has unresolved slots (never marked Completed/No-show) reported **Delayed** indefinitely to staff and the patient — found during T037 live verification; the spec was silent. What should it show? → A: Hide it — a session whose own date is before the current operational day returns `applicable: false`, so the indicator disappears for both staff and the patient (BR-016).

## Relationship to the Existing Feature

This specification **extends** the existing Session Delay Tracking feature (`backlog/023-session-delay-tracking.md`, implemented as `SessionDelayService`/`SessionDelayController` on the backend and the `DelayIndicator` component on the frontend). It does not replace, redesign, or duplicate it.

**This spec deliberately reverses one explicit prior decision**: backlog 023 states delay tracking is "NOT a live/continuous timer" and lists "Live/continuously-updating delay timers" under its own "Explicitly Out of Scope" section, because at the time no continuous-update requirement existed. The product owner's request here is precisely that continuous, train-tracking-style live status — this specification documents that reversal explicitly (rather than silently contradicting the prior spec) and treats it as approved by virtue of this request.

Everything backlog 023 already got right stays true: Fixed-Time sessions only (Queue-mode sessions continue to use queue position, feature 024, untouched by this spec), and the existing `SessionDelayController`/`SessionDelayService` clinic- and doctor-scoping pattern is reused for every new endpoint this feature adds.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Staff/doctor sees live, accurate schedule status on the Day Sheet (Priority: P1)

As front-desk staff or the treating doctor, I want the existing session view to show whether the doctor is on time, running early, or delayed — updating on its own as patients are seen — so I can set expectations with waiting patients without needing to manually refresh or do the math myself.

**Why this priority**: This is the core value of the enhancement and the direct continuation of the existing feature's purpose (023's own user story). Everything else builds on this calculation existing and being correct.

**Independent Test**: Open a Fixed-Time session's Day Sheet view partway through the day, with some slots completed and the current time past some scheduled slot times. Confirm the displayed status (On time / Running early / Delayed, with a minutes figure where applicable) matches the doctor's actual progression through the schedule, and that it updates within one polling interval of a status-changing action (marking a patient Appeared or Completed) without a manual page refresh.

**Acceptance Scenarios**:

1. **Given** a Fixed-Time session with slots at 09:00, 09:15, 09:30, 09:45, 10:00 and the doctor has completed exactly the first two and is currently seeing the third, **When** the time is 09:30, **Then** the status shows **On time**.
2. **Given** the same schedule, **When** the time is 09:30 but the doctor is still seeing the patient from the 09:00 slot, **Then** the status shows **Delayed** with a minutes-late figure, without the page being manually refreshed.
3. **Given** the same schedule, **When** the doctor has already completed the 09:45 slot and the time is 09:30, **Then** the status shows **Running early**.
4. **Given** the session's first slot is 09:00 and the current time is 08:45, **When** the status is displayed, **Then** it shows **Not started yet** — never "delayed."

---

### User Story 2 - Patient sees a simple live status for their own upcoming visit (Priority: P2)

As a patient with an upcoming Fixed-Time booking, I want to see a simple, live-updating status for the doctor I'm booked with — on time, running early, or how many minutes delayed — plus an estimated wait, so I can decide when to leave for the clinic or how much longer to wait, without needing to call the front desk.

**Why this priority**: Directly requested by the product owner and delivers real patient-facing value, but depends on User Story 1's calculation existing first — it's the same computation, exposed through a second, more restricted view.

**Independent Test**: As a patient with an active Fixed-Time booking, open the booking's detail page. Confirm it shows the doctor's name, a same-privacy-safe current-patient indicator, a plain-language schedule status, and an estimated wait — and that the status updates on its own as the doctor's actual progression changes, without exposing any other patient's name or personal information.

**Acceptance Scenarios**:

1. **Given** a patient has an active Fixed-Time booking for a session that is currently delayed by 12 minutes, **When** they view their booking, **Then** they see a schedule status reading something like "12 min delayed" and an estimated wait, with no other patient's name or contact information visible anywhere on the page.
2. **Given** the same booking once the doctor has caught up to schedule, **When** the patient views the page again (or it auto-refreshes), **Then** the status changes to "On time" without the patient needing to reload the page.
3. **Given** a patient's booking is for a session that has not started yet, **When** they view it before the first slot's scheduled time, **Then** they see a "Not started yet" style status, not a delay figure.

---

### User Story 3 - Operational-day boundary is correctly applied everywhere this feature reads "today" (Priority: P3)

As the system computing live status, I need to treat the clinic's operational day as starting at 04:30 AM (not midnight) so that a session running past midnight, or a status check made in the early hours before a clinic opens, is attributed to the correct day consistently.

**Why this priority**: Narrower in end-user-visible impact than P1/P2, but a correctness foundation both depend on — without it, "first slot of the day" and "today's session" could resolve incorrectly for any clinic activity between midnight and 04:30 AM.

**Independent Test**: With a mocked/injected clock, verify that a moment at 04:29 AM resolves to the previous calendar date's operational day, and a moment at 04:30 AM (and 04:31 AM) resolves to the current calendar date's operational day, using a single shared function — no duplicated 04:30 logic in any controller, service, or query.

**Acceptance Scenarios**:

1. **Given** the current instant is 23 Sep 04:29 AM, **When** the operational date is resolved, **Then** it returns 22 Sep.
2. **Given** the current instant is 23 Sep 04:30 AM, **When** the operational date is resolved, **Then** it returns 23 Sep.
3. **Given** the current instant is 23 Sep 04:31 AM, **When** the operational date is resolved, **Then** it returns 23 Sep.

---

### Edge Cases

- **Doctor has not started**: current time is before the first participating slot's scheduled time → **Not started yet**, never shown as delayed (see User Story 1, Scenario 4).
- **Doctor starts late**: first slot passes with nobody yet marked Appeared/Completed → **Delayed**, with a minutes-late figure computed from the first slot's scheduled time.
- **Doctor catches up**: as later slots are marked Appeared/Completed faster than new slots become due, the minutes-late figure shrinks on each recalculation and the status transitions back to On time once actual and expected progression match — this must happen automatically on the next poll, with no special "catch-up" code path.
- **Doctor gets ahead**: if actual progression moves past expected progression, status shows **Running early** with a minutes-early figure — never a negative delay number.
- **Breaks**: a schedule's break window has no Slot rows at all (existing behavior, feature 055) — so a break is never counted as either an expected or actual appointment, and never contributes to delay. This falls out of the calculation automatically because break time is a gap in the slot sequence, not a slot needing progression.
- **Cancelled appointments**: a cancelled booking's slot reverts to Open (existing behavior). Open slots — whether never booked or cancelled — take no part in the expected/actual progression calculation at all, so a cancellation can never increase or decrease the displayed delay.
- **No-shows**: a slot auto-marked No-show (existing behavior, 10-minute grace period) counts as **resolved** for progression purposes — the doctor is not considered "stuck" on a no-show patient forever, and a no-show does not, by itself, register as new delay.
- **Different slot durations**: the calculation always uses each session's own configured slot interval (already snapshotted per Session at generation time) — never a hardcoded duration.
- **Multiple doctors**: every calculation is scoped to one Session (and therefore one doctor, one clinic) at a time; one doctor's status can never affect another's.
- **A doctor with more than one Fixed-Time session on the same operational day** (e.g. a morning and an evening block from two different schedules): each session is tracked and displayed **independently** — there is no cross-session aggregation (confirmed by the 2026-09-23 clarification, A5).
- **Operational-day boundary**: explicitly tested at 04:29, 04:30, and 04:31 (User Story 3).
- **Session from a past operational day left unresolved**: staff never marked the remaining slots Completed/No-show, so the calculation would otherwise report **Delayed** forever → not applicable, indicator hidden (BR-016).

## Requirements *(mandatory)*

### Business Rules — Status Model

- **BR-001**: The system MUST express live schedule status using at least these states: `NOT_STARTED`, `ON_TIME`, `RUNNING_EARLY`, `DELAYED`, `COMPLETED`. Internal status codes MUST NOT be shown to any user directly — every surface shows the human-readable form (e.g. "On time", "12 min delayed", "Running 8 min early", "Not started yet", "Session complete").
- **BR-002**: `NOT_STARTED` applies only while the current time is before the session's first *participating* slot's scheduled start time (see BR-005 for "participating"). It MUST NOT be shown as `DELAYED`.
- **BR-003**: `COMPLETED` applies once every participating slot in the session has reached a resolved state (Completed or No-show) and none remain Booked or Appeared.
- **BR-004**: `ON_TIME`, `RUNNING_EARLY`, and `DELAYED` are mutually exclusive at any given moment for a given session, and are re-derived fresh on every read — never a stale cached label.

### Business Rules — Schedule Deviation Calculation

- **BR-005**: A slot **participates** in the expected/actual progression calculation only if it currently has, or ever had, an active booking that reached at least Booked — concretely: status is `BOOKED`, `APPEARED`, `COMPLETED`, or `NO_SHOW`. A slot that is `OPEN` (never booked, or booked and later cancelled) does not participate at all — it is skipped, not just excluded from delay math. This is what makes cancellations delay-neutral (see Edge Cases) and lets breaks fall out for free (a break has no slot rows to begin with).
- **BR-006**: The **actual progression pointer** for a session is the earliest participating slot, in scheduled order, that has not yet reached a resolved state (`COMPLETED` or `NO_SHOW`) — i.e. the slot currently `BOOKED` (not yet started) or `APPEARED` (in progress). If every participating slot is resolved, there is no actual pointer and the session is `COMPLETED` (BR-003).
- **BR-007**: The **expected progression pointer** for a session, at a given moment, is the participating slot whose scheduled time window contains "now" — equivalently, the last participating slot whose scheduled start time is at or before now. If no participating slot's scheduled time has yet arrived, there is no expected pointer and the session is `NOT_STARTED` (BR-002).
- **BR-008**: Deviation is computed by comparing the **actual pointer's** scheduled start time against the **expected pointer's** scheduled start time:
  - If the actual pointer's slot is scheduled **earlier** than the expected pointer's slot, the doctor is behind: status `DELAYED`, minutes-late = the difference between the two slots' scheduled start times.
  - If the actual pointer's slot is scheduled **later** than the expected pointer's slot, the doctor is ahead: status `RUNNING_EARLY`, minutes-early = the difference.
  - If they are the same slot, status is `ON_TIME` (no minutes figure shown).
  - This is intentionally never a raw `now − first_slot_time` calculation (per the product owner's explicit instruction) — it is always a comparison of two schedule positions derived from real slot statuses.
- **BR-009**: The calculation MUST use each session's own configured slot interval and break window (already stored per Session at generation time) — never an assumed or hardcoded duration.
- **BR-010**: Server-side clock time is the only authoritative time source for every part of this calculation. Client/browser time MUST NOT be used to determine or display status (it may only be used for cosmetic purposes such as animating a "last updated Ns ago" hint, never to compute the status itself).

### Business Rules — Operational Day

- **BR-011**: The clinic operational day runs from 04:30:00 (inclusive) on a calendar date through 04:29:59.999 the following calendar date (exclusive of the next 04:30:00). A moment at or after 04:30 belongs to that calendar date's operational day; a moment before 04:30 belongs to the *previous* calendar date's operational day.
- **BR-012**: There MUST be exactly one function in the codebase that implements this rule (a single source of truth), used by every part of this feature that needs to resolve "the current operational day" or "which operational day a given instant falls in." No duplicate 04:30 comparisons anywhere else in this feature's controllers, services, frontend components, or queries.
- **BR-013**: Per the 2026-09-23 clarification, this operational-day rule applies **only** to this feature's own calculations (live status, "first slot of the day" reference, and the doctor/admin dashboard's "Operational Day" display) — it does not change any existing feature's date handling (nightly session generation's calendar-date grouping, no-show sweep timing, Day Sheet's per-calendar-date listing, etc.), none of which currently has any operational-day concept and all of which are confirmed out of scope for this spec.
- **BR-016**: Per the 2026-09-24 clarification, live status applies only to a session whose own `sessionDate` is on or after the current operational day (BR-011). A session whose `sessionDate` is before the current operational day is no longer live — both the staff/doctor and patient endpoints return it as `applicable: false` (the same not-applicable shape already used for Queue-mode sessions), regardless of whether its slots were ever resolved. A session that runs past midnight stays live until 04:30, since its date still equals the operational day until then.

### Business Rules — First Slot Reference

- **BR-014**: For a given session, the "first slot" reference point is that session's own earliest slot in scheduled order — regardless of booking status (an Open first slot still anchors the reference; it just never participates in delay math per BR-005 unless/until it's booked).
- **BR-015**: The "first slot" and "operational day" MUST NOT be derived from: database row creation time, a doctor's login time, the first patient's booking time, the current time, or the previous operational day's first slot. It comes only from the session's own generated slot schedule.

### Functional Requirements — Calculation & Data

- **FR-001**: The system MUST compute live schedule status (BR-001 through BR-010) fresh, on demand, for a Fixed-Time session — not only at the two existing trigger points (slot completion, walk-in insertion) that the current `SessionDelayService.recalculate` uses. The existing trigger-based recalculation of the stored `Session.delayMinutes` figure MAY continue to exist for any other current consumer, but it is no longer the source of truth for this feature's live status.
- **FR-002**: The system MUST expose, for a given Fixed-Time session: which participating slot is the current one (actual pointer), which slot the schedule expects at this moment (expected pointer), the status (BR-001), and the minutes-early/minutes-late figure where applicable.
- **FR-003**: The system MUST continue to treat Queue-mode sessions as entirely out of scope for this feature (`applicable: false`, matching the existing `SessionDelayResponse` contract) — this feature must not alter queue-position tracking (024) in any way.
- **FR-004**: Every current-patient reference exposed by this feature (to any audience) MUST be an ordinal position within the session's slot sequence (e.g. "Patient 3" meaning the 3rd participating slot), never a patient's name, contact detail, or any other personally identifying information.

### Functional Requirements — Real-Time Update

- **FR-005**: The doctor/admin-facing live status display MUST update on its own, without the user manually reloading the page, using the existing client-side polling pattern already established by `QueuePositionIndicator` (periodic `setInterval`-driven re-fetch, ~20-second cadence per the 2026-09-23 clarification, A4) — not a new real-time transport (no WebSocket, no Server-Sent Events introduced for this feature, even though SSE exists elsewhere in the codebase for the unrelated Inbox feature).
- **FR-006**: The patient-facing live status display (User Story 2) MUST use the same polling pattern as FR-005, mirroring `QueuePositionIndicator`'s existing `'staff' | 'patient'` mode split rather than introducing a second, differently-shaped component family.
- **FR-007**: Polling MUST distinguish, in its UI state, between "loading," "temporarily failed to load (will retry automatically)," and "not applicable to this booking/session" (Queue-mode) — mirroring `QueuePositionIndicator`'s existing status model, not a bare null-on-any-failure.

### Functional Requirements — Doctor/Admin View

- **FR-008**: The existing session-level operations view (`SessionOperationsPanel`/Day Sheet) MUST be enhanced, not redesigned, to additionally show: Current Patient (ordinal), Expected Patient (ordinal), Schedule Status (human-readable), Minutes Early/Late (where applicable), First Slot (scheduled time), and Operational Day (date). The existing Appeared/Complete actions and their current placement are unchanged.

### Functional Requirements — Patient View

- **FR-009**: A patient viewing one of their own active Fixed-Time bookings MUST be able to see: the doctor's name, a privacy-safe current-patient ordinal, a plain-language schedule status, and an estimated wait time for their own upcoming slot. This is added to the existing patient booking detail page, alongside (not replacing) the existing queue-position/cancel/visit-record sections.
- **FR-010**: The patient-facing estimated wait MUST be derived from the patient's own slot's scheduled time adjusted by the current live deviation (BR-008) — e.g. a patient scheduled for 10:15 whose session is running 12 minutes delayed sees an estimate around 10:27, expressed to the patient as a minutes-from-now wait, not a clock time. If the patient's own slot is already resolved (they've been seen), no wait estimate is shown.
- **FR-011**: The patient view MUST NOT expose any other patient's name, contact detail, or any information beyond what BR-005–BR-010/FR-004 already define as the shared, anonymized ordinal/status figures.

### Functional Requirements — Security & Access

- **FR-012**: Every new or modified endpoint in this feature MUST use the existing `SessionDelayController` clinic-scoping and doctor-self-scoping pattern (active-role-at-clinic check, then a fail-closed 404 — not 403 — when a Doctor-only caller requests a session belonging to a different doctor) rather than inventing a new authorization approach.
- **FR-013**: The patient-facing endpoint(s) MUST scope to bookings the calling patient account actually owns, following this codebase's existing patient-ownership-check pattern (the same kind of check already used for the patient's own booking detail/queue-position/clinical-record reads) — a patient must never be able to view another patient's live status by guessing or altering a booking/session identifier.

### Key Entities

- **Live Schedule Status** *(computed value, not a new persisted entity)*: for one Fixed-Time session at one moment — status (BR-001), current-patient ordinal, expected-patient ordinal, minutes early/late, first slot time, operational day date. Derived entirely from the existing `Session` and `Slot` records; introduces no new database table.
- **Session** *(existing entity, unchanged shape unless Clarification/Assumption resolution says otherwise — see A2)*: already carries `sessionDate`, `startTime`/`endTime`, `slotIntervalMinutes`, `breakStartTime`/`breakEndTime`, `doctorProfile`, `clinic`, `mode`. This feature reads these; it does not add columns per the current design (A2).
- **Slot** *(existing entity, unchanged)*: already carries `status` (Open/Booked/Appeared/No-show/Completed) and scheduled `startTime`. This feature reads `status` and `startTime` only.

### Data Requirements

- No new persisted data is required (A1/A2) — this feature reads existing `Session`/`Slot` fields only: `Session.sessionDate`, `startTime`, `endTime`, `slotIntervalMinutes`, `breakStartTime`, `breakEndTime`, `doctorProfile`, `clinic`, `mode`; `Slot.status`, `startTime`, `session`.
- If a future need arises for historical/analytics reporting on actual consultation duration (e.g. "average minutes per patient this month"), that would require new timestamp columns and is explicitly out of scope here — this spec only needs current state, not history.

### API Requirements

- A live-status read for one Fixed-Time session, usable by staff and the treating doctor, following `SessionDelayController`'s existing routing/authorization shape (clinic-scoped, doctor-self-scoped) — returning the Live Schedule Status value (status, current/expected ordinals, minutes early/late, first slot, operational day).
- A live-status read for one of the calling patient's own active Fixed-Time bookings, following the existing patient-ownership-check pattern used by this codebase's other patient-facing per-booking reads (e.g. queue position, visit record) — returning the patient-safe subset (doctor name, current-patient ordinal, status, estimated wait) with no other patient's data.
- Both reads MUST be safe to call repeatedly on a short interval (FR-005/FR-006) — cheap, read-only, no side effects, consistent with this feature having no new write path.
- Exact route paths, request/response field names, and DTO shapes are an implementation decision for `/speckit-plan`, not fixed by this spec — but both endpoints MUST return `applicable: false` (not an error) for a Queue-mode session/booking, mirroring the existing `SessionDelayResponse` contract exactly, so existing Queue-mode consumers are unaffected.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Staff/doctors viewing a Fixed-Time session's live status can correctly identify whether the doctor is ahead, behind, or on schedule without doing any mental math, in under 3 seconds of looking at the screen.
- **SC-002**: A displayed live status reflects a status-changing action (a patient marked Appeared or Completed, a booking cancelled) within moments, automatically, with no manual page refresh required, in 100% of observed cases during testing.
- **SC-003**: 0% of patient-facing live status views ever display another patient's name or contact information, verified across every status state (not started, on time, early, delayed, complete).
- **SC-004**: The operational-day boundary resolves correctly (previous vs. current calendar date) in 100% of tested instants at 04:29, 04:30, and 04:31.
- **SC-005**: A cancelled appointment or a scheduled break never changes the displayed delay/early figure for the session it belongs to, verified by before/after comparison in testing.
- **SC-006**: Patients report the estimated-wait figure as a reasonable approximation of their actual wait (qualitative feedback target, not a hard SLA — this is a "how close" figure, not a guarantee).

## Assumptions

- **A1 (informed default, not requiring approval)**: No timestamp fields (e.g. an actual "appeared at" or "consultation started at" moment) need to be added to `Slot` for this feature. The BR-005–BR-008 calculation is derived entirely from each slot's current *status* plus its already-stored *scheduled* start time — it does not need to know exactly when a status transition happened, only what the current state is and how the schedule is laid out. (This corrects an earlier hypothesis formed before the calculation was fully designed — worth stating explicitly since "do we need a schema migration" was a real open question during investigation.)
- **A2 (informed default, not requiring approval)**: Because of A1, this feature requires **no new database table and no new column** — it is a purely computed read over existing `Session`/`Slot` data, consistent with the existing feature's own read-mostly shape.
- **A3 (confirmed by 2026-09-23 clarification)**: The new 04:30 AM operational-day boundary applies **only** to this feature's own calculations (live status, first-slot reference, the dashboard's displayed "Operational Day" field). It does **not** retroactively change nightly session generation, the no-show sweep's timing, or the Day Sheet's existing calendar-date grouping — none of which have any operational-day concept today, and changing any of them would be an unreviewed behavior change to already-shipped, unrelated features. A system-wide rollout of the 04:30 boundary would be a separate, explicitly-scoped follow-up spec.
- **A4 (confirmed by 2026-09-23 clarification)**: Polling cadence for both the doctor/admin and patient live views reuses `QueuePositionIndicator`'s existing ~20-second interval — this keeps load and behavior consistent with the one existing precedent for this kind of view. A snappier interval remains a one-line tuning change, not a design change, if wanted later.
- **A5 (confirmed by 2026-09-23 clarification)**: For a doctor with more than one Fixed-Time session on the same operational day (from two different schedules, e.g. a morning and an evening block), live status is computed and displayed **independently per session** — there is no merged, doctor-wide "first slot of the day" spanning multiple sessions. Each session shows its own first slot, its own operational-day-scoped status. This matches how every existing related feature (Session Delay Tracking, Day Sheet, Session Operations) is already session-scoped, not doctor-day-scoped.
- **A6 (documented decision, not a new default — see "Relationship to the Existing Feature")**: This spec explicitly supersedes backlog 023's "not a live timer" out-of-scope note for the specific case of on-demand live status computation (FR-001). Backlog 023's original stored-and-cached `Session.delayMinutes` mechanism is left in place for any other consumer that already reads it; it is simply no longer treated as this feature's source of truth.
- Existing authentication/session/token mechanisms (staff JWT, patient JWT) are reused unchanged — no new auth mechanism is introduced.
- The clinic's effective timezone continues to be "the server's own local time," consistent with every existing time-based feature in this codebase (no clinic-level timezone entity exists today, and introducing one is out of scope for this spec).

## Testing Strategy

- **Unit tier** (new, mirroring `SessionDelayServiceTest`'s existing shape): the BR-005–BR-008 progression/deviation calculation, exercised directly against constructed slot-status sequences — covering every edge case in this spec (not started, starts late, catches up, gets ahead, a break gap, a cancelled/reverted-to-Open slot, a no-show, varying slot intervals) without needing a database.
- **Unit tier**: the single operational-day-resolution function (BR-011/BR-012), exercised at 04:29, 04:30, 04:31, and at least one additional instant well inside each side of the boundary, plus a boundary check across a month/year rollover (e.g. 31 Dec 04:29 → 30 Dec; 1 Jan 04:30 → 1 Jan).
- **Contract tier**: the new staff/doctor endpoint and the new patient endpoint, each covering: success shape, `applicable: false` for Queue-mode, 401 for no token, 404 for a doctor requesting another doctor's session, 404 for a patient requesting a booking they don't own.
- **Integration tier** (Testcontainers, written and compiling per this project's standing convention even though execution is Docker-gated in this sandbox): at least one full-lifecycle scenario per user story — a session progressing from Not started → Delayed → catching up to On time → Running early, verified against a real database and real slot-status transitions (Appeared/Completed/No-show/Cancel), not just a unit-level calculation.
- **Regression check**: confirm the existing `SessionDelayServiceTest`/`SessionDelayAuthorizationTest`/`SessionDelayQueueModeTest`/`SessionDelayReadOnlyTest`/`SessionDelayNoOutstandingDelayTest` suite still passes unchanged, proving the existing trigger-based `Session.delayMinutes` mechanism and its consumers are genuinely untouched by this addition.
- **Live/manual verification** (per this project's standing practice for UI-visible changes): a real walkthrough against running dev servers — mark a slot Appeared, confirm the doctor/admin view updates within one polling interval without a manual refresh, then the same check from a patient account viewing their own booking.
