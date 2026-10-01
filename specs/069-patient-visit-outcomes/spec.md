# Feature Specification: Patient Visit Outcomes and Valid Actions

**Feature Branch**: `claude/069-patient-visit-outcomes`

**Created**: 2026-10-01

**Status**: Draft

**Input**: Phase 2R.1 of `docs/NEXT_PHASES_ACTION_PLAN.md`, findings 1–3 of `docs/LIVE_SOFTWARE_AUDIT_2026-10-01.md`:

1. A no-show appointment is described to the patient as "Visit complete".
2. A no-show appointment stays the patient's "Your next visit" and shows as "Active".
3. The patient detail screen offers "Cancel booking" for appointments that cannot be cancelled.

## Context

Three different things are mixed up in the patient console today:

- **Booking state**: `ACTIVE` or `CANCELLED` (028). This only says whether the booking was cancelled.
- **The patient's own visit outcome**: the status of the patient's own slot (`BOOKED`, `APPEARED`, `NO_SHOW`, `COMPLETED`) from 023, 026, 057 and 064.
- **Whole-session progress**: computed by 061 `SessionLiveStatusService`, with `COMPLETED` meaning every slot in the session is resolved. A no-show counts as resolved.

The patient's summary exposes only the booking state, so a no-show reads as "Active" (finding 2). The patient live-status text is driven by session progress, so a no-show reads as "Visit complete" (finding 1). The detail page always shows the cancel button, even though the server refuses most of those cases (finding 3).

**What changes:** the server derives the patient's own visit outcome and cancellation eligibility and sends both to the patient console. All patient screens use them consistently.

**What does not change:**
- The cancellation policy: the 2-hour cutoff, fixed-time only, walk-ins not self-cancellable, BOOKED/APPEARED only.
- The cancel endpoint's own checks and errors.
- Slot and booking records: nothing is rewritten.
- Staff screens.
- The visit lifecycle (that is Phase 4).

## Decision table (source of truth for this feature)

"Today" is the server's operational date: the JVM is pinned to `Asia/Kolkata` (#29), via `SessionAvailabilityService.now()`. A visit outcome is computed from the booking state first, then the slot status, then the date:

| Booking | Slot status | Session date vs today | `visitOutcome` | Patient label | Upcoming (next-visit candidate) |
|---|---|---|---|---|---|
| CANCELLED | any | any | `CANCELLED` | Cancelled | no |
| ACTIVE | COMPLETED | any | `COMPLETED` | Visit complete | no |
| ACTIVE | NO_SHOW | any | `NO_SHOW` | Missed appointment | no |
| ACTIVE | APPEARED | today or later | `CHECKED_IN` | Checked in | yes |
| ACTIVE | BOOKED (or a legacy OPEN queue token) | today or later | `SCHEDULED` | Upcoming / Today | yes |
| ACTIVE | BOOKED, APPEARED or OPEN | before today | `NOT_RECORDED` | Outcome not recorded | no |

**Delayed and queue rule (the only behaviour that was undefined before).** A booking dated **today** that is still `BOOKED` or `APPEARED` remains the next visit, **even after its scheduled time has passed**.
- A running-late session must not hide a patient who is still waiting.
- The no-show sweep (023) or staff action resolves it, and elapsed time is never treated as completion.
- Queue and untimed walk-in bookings follow the same rule: they have no start time, so only their date matters.
- On a later day an unresolved booking is `NOT_RECORDED`. It is no longer upcoming, but it is also not labelled completed or missed.

**Next-visit selection.** Among upcoming bookings, the earliest one wins, ordered by session date, then by start time with untimed entries after timed ones, then by token number.

**Cancellation eligibility** (patient self-cancel). The first refusal that applies wins, ordered by how informative it is:

| # | Condition | `reason` |
|---|---|---|
| 1 | Booking is CANCELLED | `ALREADY_CANCELLED` |
| 2 | Slot is NO_SHOW or COMPLETED | `VISIT_RESOLVED` |
| 3 | Session is QUEUE mode | `QUEUE_BOOKING` |
| 4 | Untimed walk-in slot | `WALK_IN` |
| 5 | Scheduled start is less than 2 hours away, or already passed | `CUTOFF_PASSED` |
| — | none of the above | allowed (`reason` = null) |

The cancel endpoint keeps its existing check order (queue, then walk-in, then cutoff, then slot or booking state) and its existing error codes. The displayed eligibility is advisory only; the endpoint always re-checks.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A missed appointment is shown as missed (Priority: P1)

A patient missed their 11:00 appointment and the sweep marked it No-show. In My bookings, on the booking detail and in its live-status text, the patient sees "Missed appointment", never "Visit complete" or "Active".

**Independent Test:** Create a synthetic booking whose slot is NO_SHOW in a session where every slot is resolved. Fetch the summary, the detail and the live status. Each reports a no-show outcome, and none says "Visit complete".

**Acceptance Scenarios**:

1. **Given** the patient's own slot is NO_SHOW and the session's other slots are completed, **When** the patient opens the booking detail, **Then** the outcome is "Missed appointment" and the live-status text does not say "Visit complete".
2. **Given** the patient's own slot is COMPLETED, **When** they open the detail, **Then** it says "Visit complete".
3. **Given** a cancelled booking, **When** it is listed, **Then** it shows "Cancelled", including the existing clinic-rejected note.

### User Story 2 - "Your next visit" is truly the next visit (Priority: P1)

The dashboard's next-visit card shows the earliest upcoming booking from the table above. It never shows a no-show, completed, cancelled or past unrecorded booking.

**Independent Test:** Give one patient a no-show booking today, a completed booking today, a cancelled booking tomorrow, an unresolved booking yesterday and a scheduled booking in five days. The next-visit card shows the five-day booking.

**Acceptance Scenarios**:

1. **Given** the bookings above, **When** the dashboard loads, **Then** "Your next visit" is the five-day booking.
2. **Given** a booking today whose start time has passed but which is still BOOKED, **When** the dashboard loads, **Then** it is still the next visit.
3. **Given** only terminal or past bookings, **When** the dashboard loads, **Then** no next-visit card is shown.

### User Story 3 - Cancel is offered only when it can succeed (Priority: P1)

The detail page shows "Cancel booking" only when the server says the booking is cancellable. Otherwise it explains why, for example: "This appointment can no longer be cancelled online: it starts within 2 hours."

**Independent Test:** For each refusal reason, open the detail page. The button is absent, and the matching explanation is shown.

**Acceptance Scenarios**:

1. **Given** each ineligible case in the eligibility table, **When** the detail loads, **Then** no cancel action is offered and the matching explanation is shown.
2. **Given** an eligible booking, **When** the detail loads, **Then** the existing cancel flow (with its reason picker) is offered.
3. **Given** the detail was loaded while the booking was eligible, but the cutoff passes before submit, **When** the patient confirms, **Then** the server still refuses with the existing cutoff error, and the UI shows it.

### Edge Cases

- **Another patient's booking id:** the new detail endpoint returns 404, exactly like the live-status and cancel endpoints.
- **A legacy OPEN queue token on an active booking:** treated as BOOKED.
- **APPEARED on a past day** (auto-complete never ran): `NOT_RECORDED`.
- **The session is whole-cancelled (065):** its bookings are already CANCELLED, so the outcome is `CANCELLED`.
- **Live status when the session is not running** (not today): unchanged, not applicable.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Each patient booking summary MUST include `visitOutcome`, derived exactly as in the decision table.
- **FR-002**: Each patient booking summary MUST include `cancellation` = `{ allowed, reason }`, derived exactly as in the eligibility table, using the same rules the cancel endpoint enforces.
- **FR-003**: A new `GET /api/v1/patients/bookings/{bookingId}` MUST return that booking's summary, with the FR-001 and FR-002 fields, only to its owner. Anyone else, including another patient, gets 404.
- **FR-004**: When the patient live-status panel is applicable (unchanged rule: a fixed-time session on its operational day), its text MUST reflect the patient's own outcome once that outcome is terminal:
  - "Visit complete" only for the patient's own COMPLETED slot;
  - "Missed appointment" for NO_SHOW;
  - "Booking cancelled" for a cancelled booking.

  For a non-terminal outcome, the session-progress text is unchanged. The response MUST also include `visitOutcome`. When the panel is not applicable, the detail page's outcome header (FR-007) still shows the outcome.
- **FR-005**: The cancel endpoint's checks, check order, errors and race safety MUST be unchanged. It MUST still refuse stale attempts on its own.
- **FR-006**: The dashboard next-visit card MUST select per the next-visit rule. My bookings and the booking detail MUST label bookings per the decision table.
- **FR-007**: The detail page MUST offer cancellation only when `cancellation.allowed`. Otherwise it MUST show a reason-specific explanation.
- **FR-008**: No booking or slot record is modified by any of these reads. There is no schema change.

### Key Entities

- **Visit outcome**: derived and read-only, not stored.
- **Cancellation eligibility**: derived and read-only, not stored.

## Success Criteria *(mandatory)*

- **SC-001**: A no-show is presented as "Visit complete" on 0 patient screens and API responses.
- **SC-002**: The next-visit card shows a no-show, completed, cancelled or past unrecorded booking 0 times.
- **SC-003**: The detail page offers a cancel action that the server would refuse 0 times, given the state at load time. Stale actions are still refused by the server.
- **SC-004**: My bookings, the detail page and the dashboard show the same outcome for the same booking.

## Assumptions

- The no-show sweep and staff actions remain the only producers of terminal slot states; this feature only reads them.
- "Today" follows the server's operational zone. The browser does not compute upcoming status itself.
