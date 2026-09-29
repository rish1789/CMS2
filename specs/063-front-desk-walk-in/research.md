# Research: Front-Desk Walk-In Registration (063)

All findings come from reading the code on 2026-09-24. Decisions reuse existing mechanisms wherever one exists.

## Decision 1 — The walk-in line is untimed token slots inside the Fixed-Time session

**Decision**: A Fixed-Time walk-in gets a Slot in the chosen session with `startTime = null`, `endTime = null` and the next `tokenNumber` (W1 = token 1, W2 = token 2…). This is the same slot shape Queue sessions already use, and the same token counter:
- `SlotRepository.findMaxTokenNumberBySession_Id`;
- the unique index on `(session_id, token_number)` from V11;
- `QueueSlotService`'s retry loop.

`QueueSlotService` gains `issueNextWalkInSlot(sessionId)`, which requires `FIXED_TIME`. It is the mirror of the existing QUEUE-only `issueNextSlot` and shares its private issuance core. **Result: no second queue system.**

**Status**: the walk-in slot is set to `BOOKED` at registration, like any booked fixed-time slot. Three reasons:
- The existing "Appeared" action accepts only `BOOKED`/`NO_SHOW` (`SlotAppearedService`).
- The existing "pending booking" queries (008, 037, 062) already treat `BOOKED` as pending.
- A removed walk-in reverts to `OPEN` through the existing cancel path, which is harmless given Decision 3's filters.

**Alternatives considered**:
- A separate walk-in table or queue entity — rejected by the product decision (no second queue).
- Leaving walk-in slots `OPEN`, as queue tokens are — rejected, because Appeared would refuse them.

## Decision 2 — "Send in" = existing Appeared; "finished" = existing Complete; both now timestamped

**Decision**:
- Add nullable `appeared_at` and `completed_at` columns to `slot`.
- `SlotAppearedService.markAppeared` stamps `appearedAt`.
- `SlotCompletionService` (manual and automatic) stamps `completedAt`.

This applies to every visit (spec Assumption). No new action is added.

**Guard change**: `SlotCompletionService`'s "not yet started" check builds a time from `slot.getStartTime()`. It is skipped when `startTime` is null, because an untimed walk-in has no scheduled start.

**Scope (spec clarification, 2026-09-24)**: Appeared and Complete exist only for Fixed-Time sessions. Queue sessions have no send-in or complete step at all today. Adding them would change booked queue appointments, which is out of scope. Therefore "Send in", the "Doctor free now" hint and the visit times apply to **Fixed-Time sessions**. Queue walk-ins take the next token, as decided, and behave like every other token.

## Decision 3 — Every "fixed-time slot has a time" assumption is guarded

An audit of every `slot.getStartTime()`/`getEndTime()` use, and every Fixed-Time slot query, found these places that would misbehave on an untimed slot in a Fixed-Time session. Each gets a targeted guard.

| Place | Problem | Guard |
|---|---|---|
| `NoShowDetectionService` + `SlotRepository.findBookedFixedTimeCandidatesForNoShow` | NPE building the scheduled time | Query adds `s.startTime IS NOT NULL` (walk-ins are never auto no-showed) |
| `SlotAutoCompletionService` + `findAppearedFixedTimeCandidatesForAutoCompletion` | NPE on `getEndTime()` | Query adds `s.startTime IS NOT NULL` (a walk-in stays In-with-doctor until completed) |
| `SlotCompletionService` "not yet started" | NPE | Skip when `startTime` is null |
| `SlotRepository.findOpenFixedTimeSlots` / `...OnDate` (patient slot listing, 2 variants + count queries) | A removed walk-in's OPEN untimed slot would be offered for booking | Add `s.startTime IS NOT NULL` |
| `StaffBookingService.bookSlot` / `PatientBookingService.bookSlot` by slot id | Could book an untimed slot directly | Refuse an untimed slot as not found (`SlotNotFoundException`) |
| `WaitlistBumpListener` (consumer of `BookingCancelledEvent`, published by staff cancel and partial session cancel) | Would offer an untimed walk-in slot to the waitlist | Skip untimed slots at the single consumer, which covers every publisher (analyze U1) |
| `PatientBookingCancellationController` cutoff | NPE building the scheduled time | Refuse an untimed booking with new 409 `WALK_IN_NOT_SELF_CANCELLABLE`: walk-ins are present at the clinic, and staff remove them (FR-015) |
| `SessionPartialCancellationService` (cancel from a cutoff time) | NPE on `startTime` | Skip untimed slots; a cutoff time can't place a walk-in before or after it. Staff remove waiting walk-ins individually. |
| `SlotRepository.countBySessionIdIn` (session list booked/total counts) | Walk-ins inflate the session's slot capacity | Count only timed slots for Fixed-Time sessions: `(s.startTime IS NOT NULL OR s.session.mode = QUEUE)` |

Already null-safe, no change needed:
- `SessionLiveStatusService` (filters `startTime != null`; patient estimated-wait returns null);
- `SessionDelayService` (same filter);
- `FlagDetectionService.overlaps` (null-guarded);
- Day Sheet ordering (`ORDER BY startTime, tokenNumber`: Postgres sorts nulls last, so walk-ins come after timed slots).

## Decision 4 — One front-desk registration service; the old walk-in insertion is retired

**Decision**: A new `FrontDeskWalkInService.register(caller, clinicId, input)` in the booking module does the following, in order:
1. Load the session (scoped to the clinic).
2. Authorize: Operations or ClinicAdmin, as today.
3. Apply the rejected-clinic refusal (062).
4. Validate the visit reason.
5. Check for a duplicate (FR-017), unless `confirmDuplicate` is set.
6. Resolve and lock the fee (existing `FeeResolutionService`).
7. Resolve or create the patient: existing clinic patient by id, or new with name, optional phone (existing validator) and optional email.
8. Issue the slot: FIXED_TIME → `issueNextWalkInSlot` + `BOOKED`; QUEUE → existing `issueNextSlot`.
9. Save a `WALK_IN` booking with the visit reason.
10. Create the existing inbox walk-in item (`InboxItemService.createWalkInItem`).

It returns the placement: token number, position in the walk-in line (Fixed-Time), fee, doctor, patient.

**Retired** (FR-016; product decision overriding 025/058):
- `WalkInInsertionService`, `WalkInInsertionController`, `WalkInRequest`;
- the exceptions used only by them;
- the frontend `WalkInForm` with its test and route.

The old route `sessions/:sessionId/walk-in` redirects to the new screen with the session pre-selected.

**Existing data is untouched**: `booking.override_reason` stays as a column, and old rows keep their values.

**Test impact**:
- The WalkIn* integration tests (025) are deleted together with the behavior they tested.
- `AbstractInboxIntegrationTest.insertWalkIn` and the 062 refusal tests are rewritten to drive the new service.

**Why a new service rather than extending `StaffQueueBookingService`**: that service creates `SCHEDULED` bookings for staff-assisted queue booking, which stays unchanged (spec: booked appointment behavior unchanged). The front-desk service composes the same collaborators (`QueueSlotService`, `FeeResolutionService`) instead of duplicating their logic.

## Decision 5 — Visit reason and patient email storage

**Decision** (one migration, `V39__front_desk_walk_in.sql`):
- `booking.visit_reason VARCHAR(40)` holds a `VisitReason` enum: `FEVER_COLD_COUGH, PAIN, FOLLOW_UP, TEST_REPORT_REVIEW, PRESCRIPTION_REFILL, INJURY, GENERAL_CHECKUP, OTHER`.
- `booking.visit_reason_detail VARCHAR(200)`.
- `patient.email VARCHAR(254)`.
- `slot.appeared_at TIMESTAMPTZ`, `slot.completed_at TIMESTAMPTZ`.

All nullable: existing rows have none of these, and booked (non-walk-in) visits don't collect a reason. The "required" rule is enforced by the walk-in service. A DB check constraint requires `visit_reason_detail` when `visit_reason = 'OTHER'` (Constitution I: a migration invariant with a test).

## Decision 6 — Doctor step data reuses the existing session list, plus two counts

**Decision**: `GET /api/v1/clinics/{clinicId}/sessions?from=today&to=today` already lists today's sessions with doctor, time, mode and booked/total. `SessionSummaryResponse` gains `walkInsWaiting` and `inWithDoctor` (a Fixed-Time session has an `APPEARED` slot), from one grouped query per page, the same bulk shape as `countBySessionIdIn`.
- **Live schedule status**: the existing `/live-status` endpoint per session, polled at the existing ~20 s cadence.
- **Readiness**: the existing `DoctorBookingReadinessService` endpoint.

"Doctor free now" = `!inWithDoctor`, computed from the same `APPEARED` status the Day Sheet already shows. No new status logic (FR-012).

## Decision 7 — Queue position

For a Fixed-Time walk-in, position = the number of the session's untimed `BOOKED` walk-in slots with a lower token, plus 1. That's the same rule as `QueuePositionService`, which is extended to handle untimed slots in Fixed-Time sessions.

For Queue sessions, `QueuePositionService` counts only `BOOKED` tokens ahead, but real queue bookings leave their token `OPEN` (a pre-existing 024 bug, the same family as the 2026-09-24 008/037 fixes). So the queue-session confirmation shows the token number only. The bug is flagged separately and not changed here.

## Decision 8 — Frontend

- **New `features/front-desk-walk-in/`**: a stepper page (Patient → Visit reason → Doctor session → Confirm → Result) and a "Walk-in line" panel for the selected Fixed-Time session. The panel shows the waiting list in arrival order, the Doctor free/busy hint, "Send in" (existing Appeared endpoint), "Complete" (existing endpoint) and "Remove" (existing staff cancel endpoint).
- **Reused as they are**: `PatientPicker`, patient search (phone match), `AppointmentTypeSelect`, and the live-status API client.
- **Day Sheet**: walk-ins render in a separate "Walk-in line" section with a walk-in badge and the visit reason. The per-session buttons link to `/staff/clinics/:clinicId/walk-in?sessionId=…`.
- **Staff navigation**: a "Walk-in" entry.
