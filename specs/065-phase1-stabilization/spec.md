# Feature Specification: Phase 1 Stabilization — Security Boundary and Booking Correctness

**Feature Branch**: `065-phase1-stabilization`

**Created**: 2026-09-29

**Status**: Draft

**Input**: User description:

> "065 — Phase 1 Stabilization: security boundary and booking-correctness fixes, sourced from `docs/product-audit/07-BUG-AND-DEFECT-REGISTER.md` and `08-SECURITY-AUDIT.md` (SEC-01/BUG-001, SEC-02, BUG-002…BUG-006; investigate PB-005, SEC-03, SEC-06/PB-008). Fixes only, test-first, no new product features."

## Documented product decisions (constitution: explicit decisions, no silent deviation)

The project owner's Phase 1 brief (2026-09-29) supersedes two earlier clarifications:

- **Reverses spec 029** (Clarification Q1, FR-008) and **spec 030** (Clarification Q1). Those specs deliberately returned cancelled slots to `OPEN` and introduced no session-level cancelled concept.
- **New rule:** a session cancelled as a whole accepts no new bookings of any kind, and a session cancelled from a cutoff time accepts no new bookings at or after that cutoff.
- **Unchanged:** the waitlist-bump exclusion for whole and partial cancellations (029 FR-003, 030 FR-005).

The brief also extends spec 034's scope, which explicitly did not re-check staffing status:

- **New rule:** *creating* new clinical documentation (consultation note, prescription, external record reference) requires the treating doctor to still hold an active Doctor role at that clinic.
- **Unchanged:** reading already-written documentation remains governed by the existing treating-doctor rule (historical visibility is preserved).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Protected endpoints refuse anonymous callers at the boundary (Priority: P1)

As the clinic operator, I need every non-public endpoint to reject a caller with no valid session before any clinic logic runs, so that a newly added endpoint can never become public by omission.

**Why this priority**: This is a security boundary defect (fail-open default) with seven live instances.

**Independent Test**: Call every affected endpoint, plus an arbitrary unmapped path under each protected prefix, without credentials. Confirm "unauthenticated" responses. Confirm the public endpoints still work without credentials.

**Acceptance Scenarios**:

1. **Given** no credentials, **When** any of the seven audit-listed staff endpoints is called, **Then** the response is "unauthenticated" (401), never a server error.
2. **Given** no credentials, **When** an unlisted path under the staff or patient prefix is called, **Then** the response is 401.
3. **Given** no credentials, **When** clinic registration, staff login, patient signup, patient login, or public discovery is called, **Then** the request is processed as before (not 401).
4. **Given** a valid staff session with no role at clinic B, **When** a clinic-B endpoint is called, **Then** the response is still "forbidden" (403), unchanged.

---

### User Story 2 - A cancelled session, or cancelled range, can never be booked again (Priority: P1)

As front-desk staff, when I cancel a doctor's session, or cancel it from a cutoff time because the doctor leaves early, I need that period to stop accepting bookings, so that no patient is booked with an absent doctor.

**Why this priority**: The current behaviour silently reopens the cancelled capacity to patients and staff (BUG-002, BUG-003).

**Independent Test**: Cancel a session (whole, or from a cutoff). Then attempt every booking path against it: patient fixed-time, staff fixed-time, queue token (patient and staff), walk-in. Confirm the affected slots no longer appear in patient slot listings or upcoming queue sessions.

**Acceptance Scenarios**:

1. **Given** a fixed-time session with booked and open slots, **When** staff cancel the whole session, **Then** every active booking is cancelled (as today), and no slot of that session is listed to patients or accepted by any booking path afterwards.
2. **Given** a queue-mode session, **When** it is cancelled as a whole, **Then** it no longer appears among a patient's bookable queue sessions, and new tokens (patient, staff, walk-in) are refused.
3. **Given** a fixed-time session, **When** staff cancel from 16:00, **Then** slots starting before 16:00 remain bookable (subject to the other rules), and slots starting at or after 16:00 are neither listed nor bookable.
4. **Given** a session cancelled from a cutoff, **When** a queue token or walk-in is requested at or after the cutoff time on the session date, **Then** it is refused. Before the cutoff it is accepted. This follows spec 030 FR-008's token-issuance-time proxy.
5. **Given** a refused booking on a cancelled session or range, **When** the caller receives the response, **Then** it states that the session is not accepting bookings. It is not a generic error.

---

### User Story 3 - An empty session can be cancelled; a second cancellation is reported honestly (Priority: P2)

As staff, I need to cancel a future session that has no bookings yet (the doctor will be absent), and to be told clearly if the session was already cancelled.

**Why this priority**: Without it, an empty session stays open for booking (BUG-004). Only deletion is available today, and deletion is ClinicAdmin-only.

**Independent Test**: Cancel an empty future session and confirm success with zero bookings affected. Cancel it again and confirm "already cancelled". Cancel a session whose visits are all Appeared or No-show and confirm success with those visits untouched.

**Acceptance Scenarios**:

1. **Given** a future session with no bookings, **When** staff cancel it, **Then** the cancellation succeeds reporting 0 cancelled bookings, and the session accepts no new bookings.
2. **Given** an already whole-cancelled session, **When** staff cancel it again, **Then** the response is "already cancelled" (409) and nothing changes.
3. **Given** a session whose only visits are Appeared or No-show, **When** staff cancel it, **Then** it succeeds, those visits are left as they are, and no new bookings are accepted.
4. **Given** a session previously cancelled from a cutoff, **When** staff cancel the whole session, **Then** it succeeds and the whole session becomes unavailable.

---

### User Story 4 - Elapsed times are never offered or accepted (Priority: P2)

As a patient, I should only be offered appointment times that have not yet started. As staff, I should not be able to book a timed slot that has already started. Patients who are already present use the walk-in flow.

**Why this priority**: Booking an elapsed slot today produces an immediate automatic no-show (BUG-005).

**Independent Test**: With a controllable server clock, list and book slots: one later today, one starting exactly now, one earlier today, one tomorrow, and one on a past date.

**Acceptance Scenarios**:

1. **Given** the server time is 10:00, **When** a patient lists today's slots, **Then** the 10:00 and 10:30 slots are listed, and the 09:30 slot is not.
2. **Given** the server time is 10:00, **When** a patient or staff member books the 09:30 slot, **Then** it is refused as no longer available. The 10:00 slot (starting exactly now), the 10:30 slot and tomorrow's slots are accepted.
3. **Given** a slot on a past date, **When** any fixed-time booking is attempted, **Then** it is refused (as today for patients; newly also for staff).
4. **Given** a queue or walk-in request for a session on a past date, **When** it is submitted, **Then** it is refused.

---

### User Story 5 - Only currently-staffed doctors can create clinical records (Priority: P2)

As the clinic, I need a doctor whose role at my clinic has been deactivated to be unable to add new consultation notes, prescriptions or external record references for my clinic's patients.

**Why this priority**: A privacy and integrity gap (SEC-06).

**Independent Test**: Deactivate the treating doctor's role at the clinic, then attempt to create each document type (refused) and read the existing ones (still allowed).

**Acceptance Scenarios**:

1. **Given** a treating doctor with an active Doctor role, **When** they create any clinical document, **Then** it succeeds (unchanged).
2. **Given** a treating doctor whose Doctor role at that clinic is inactive, **When** they create any clinical document, **Then** it is refused as forbidden.
3. **Given** the same deactivated doctor, **When** they read documentation they previously wrote for that booking, **Then** it is returned (unchanged).

---

### User Story 6 - Developer configuration carries no secrets; the test suite is stable (Priority: P3)

As a maintainer, I need the shared run configuration to contain no credentials, and the frontend test suite to pass reliably on a loaded machine.

**Independent Test**:
- The run configuration contains no literal secret values and still starts the backend when the developer's environment provides them.
- The full frontend suite passes repeatedly under full-suite load.

**Acceptance Scenarios**:

1. **Given** the shared backend run configuration, **When** it is inspected, **Then** it contains no literal credential or signing-secret values.
2. **Given** the full frontend test suite, **When** it runs on the same machine that previously produced 6 timeouts, **Then** all tests pass, without raising the global default timeout.

### Edge Cases

- Cancelling a session's range twice with different ranges: both ranges stay unavailable. Nothing previously unavailable becomes available again.
- Whole cancellation after a partial one: allowed; the whole session becomes unavailable.
- A partial cancellation after a whole one: refused as "already cancelled".
- A booking already in progress when the session is cancelled: the existing database guarantees stand. A booking that commits before the cancellation is cancelled by it, if it is Booked.
- Past-date session cancellation: behaviour is unchanged. No new date restriction is added to cancellation.
- Slots before a partial cutoff whose start time has already elapsed: unavailable because of Story 4, not because of the cutoff.
- A walk-in after a fixed-time session's scheduled end on the same day remains allowed (spec 063 "Session over" rule), unless the session or range is cancelled.
- Existing cancelled sessions created before this change are indistinguishable from never-cancelled sessions. No backfill: historical cancellations carried no marker, and inferring one would be guesswork.

## Requirements *(mandatory)*

### Functional Requirements

**Security boundary**

- **FR-001**: Every endpoint under the staff and patient prefixes MUST require a valid session of that realm, except these explicit public endpoints: clinic registration, patient signup, and patient login.
  - Staff login and public discovery live under their own public prefixes, unchanged.
  - Super Admin and doctor-configuration prefixes already deny anonymous access, unchanged.
- **FR-002**: An anonymous request to a protected endpoint MUST receive 401 with the existing "unauthenticated" error body, without executing the endpoint.
- **FR-003**: Existing per-clinic authorization (403 for an authenticated caller without the required active role) MUST be unchanged.
- **FR-004**: The shared developer run configuration MUST NOT contain literal credential or signing-secret values. Values MUST come from the developer's environment, with no hard-coded replacement.

**Session cancellation**

- **FR-005**: The system MUST durably record each cancellation of a session:
  - either the whole session, or a time range (from a cutoff, with the optional existing upper bound `toTime`)
  - plus when it happened
  - several range cancellations of one session MUST all be honoured
- **FR-006**: A whole-cancelled session MUST NOT accept any new booking through any path: patient fixed-time, staff fixed-time, patient queue, staff queue, walk-in.
- **FR-007**: For a session with a cancelled range:
  - It MUST NOT accept a new fixed-time booking for a slot whose start time falls inside the range (at or after the cutoff, and before the optional upper bound).
  - It MUST NOT accept a new queue token or walk-in while the current time on the session date falls inside the range.
- **FR-008**: Patient slot listings and patient queue-session listings MUST exclude cancelled sessions and cancelled ranges.
- **FR-009**: Cancelling a session with no Booked slots MUST succeed (reporting 0 cancelled bookings) unless the session is already whole-cancelled, in which case it MUST be refused as already cancelled.
- **FR-010**: Existing cancellation outcomes are unchanged:
  - active Booked bookings are cancelled
  - notification events are emitted as today
  - no waitlist bump
  - Appeared, No-show and Completed visits are untouched
- **FR-011**: Repeated range cancellations are cumulative. A time unavailable because of any earlier cancellation stays unavailable.

**Elapsed times**

- **FR-012**: A fixed-time slot is *elapsed* when its session date and start time are strictly before the current server time; a slot starting exactly now is still bookable (owner decision, 2026-09-29). Elapsed slots MUST NOT be listed to patients, and MUST NOT be bookable by patients or staff.
- **FR-013**: Staff fixed-time, patient queue, staff queue and walk-in bookings MUST be refused for sessions dated before today (server date).
- **FR-014**: "Current time" for these rules MUST come from the server, through an injectable clock, so the rules are deterministic in tests. It uses the same time zone the rest of the server already uses (see Assumptions).

**Clinical documentation**

- **FR-015**: Creating a consultation note, prescription or external record reference MUST additionally require that the treating doctor holds an active Doctor role at the booking's clinic.
- **FR-016**: Reading existing documentation is unchanged.

**Test stability**

- **FR-017**: The frontend suite MUST pass reliably under full-suite load, without raising the global default test timeout.

### Key Entities

- **Session cancellation record** (new): belongs to one session. It captures:
  - the kind: whole session, or a time range (from-time plus optional to-time)
  - when it was recorded

  It is written once and never edited. Having no records means the session is not cancelled.
- **Session** (existing): unchanged columns. Its bookability is derived from its cancellation records.
- **Slot** (existing): unchanged states. After cancellation a slot may still read as `Open`, but it is not bookable because of its session's cancellation record.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 0 protected endpoints respond with anything other than 401 to an anonymous caller. Verified for the 7 audit-listed endpoints and for unlisted paths.
- **SC-002**: 0 new bookings can be created, by any of the 5 booking paths, against a whole-cancelled session, or at or after a cancellation cutoff.
- **SC-003**: Staff can take an empty future session out of service in 1 action (previously impossible without deletion).
- **SC-004**: 0 elapsed timed slots are offered to patients or accepted from any caller.
- **SC-005**: 0 new clinical documents can be created by a doctor with no active role at the clinic.
- **SC-006**: The frontend suite passes in 3 consecutive full runs on the audit machine, with no global timeout increase.
- **SC-007**: The shared run configuration contains 0 literal secret values.

## Assumptions

- **Time zone (PB-005, investigated):** the product targets Indian clinics only (Indian mobile validation, INR fees, Indian cities). No deployment configuration exists in the repository that sets a different JVM zone. Every business rule today uses the server's default zone. This feature keeps that single-zone behaviour and makes the new rules clock-injectable. A per-clinic or explicitly configured zone is deferred to a product and deployment decision.
- **Fee isolation (SEC-03, investigated):** appointment types and default fees are deliberately doctor-global, editable by any active ClinicAdmin at a clinic where the doctor works (backlog 015 Business Rules; spec 017 FR-005/FR-006). This is specified behaviour, not a defect. Changing it requires a product decision and is out of scope.
- **Out of scope:**
  - reschedule, payments, notification delivery or preferences
  - availability exceptions, audit trail
  - UI redesign
  - other potential issues in the audit (PB-001…PB-004, PB-006, PB-007, PB-009, PB-010) not listed above
  - the booking-state precondition on clinical documents (PB-008 second half)
- **Existing whole or partial cancellations** made before this change are not backfilled (see Edge Cases).
- **"Starting exactly now"** is still bookable (owner decision 5, 2026-09-29): elapsed means start time strictly before now.
