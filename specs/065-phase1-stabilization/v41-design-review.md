# V41 Design Review — Session Cancellation (BUG-002/003/004) and Elapsed Slots (BUG-005)

**Status:** APPROVED by the owner on 2026-09-29, with these decisions:
1. Whole-session cancellations are never automatically bookable again.
2. Multiple partial ranges per session are allowed.
3. The day sheet and UI reflect cancellations.
4. No backfill.
5. **A timed slot is unavailable only when `start_time < now`; equality stays bookable.** This supersedes the "start == now counts as elapsed" proposal below.
6. Past sessions may still be cancelled.
7. `cancelled_by_account_id` is added.
8. Tests that preserve BUG-002/003 behaviour are updated.

(Original proposal text follows. Where it conflicts with the decisions above, the decisions win.)
**Date:** 2026-09-29.
**Evidence:** [code] = read in source this session; [spec] = spec or backlog text.

---

## 1. Current session/slot lifecycle (traced in code)

| # | Flow | UI | API | Service / rule | Database | Resulting state |
|---|---|---|---|---|---|---|
| 1 | Session creation | Staff `ScheduleForm` (schedule only); Super Admin "Generate sessions" | `POST /clinics/{c}/doctors/{d}/schedules`; `POST /admin/sessions/generate`; nightly `NightlySessionGenerationTrigger` (02:00) | `ScheduleSessionGenerator.generateForSchedule`: for each date within 15 days matching the schedule's weekdays, **skips dates that already have a session row for that schedule** (`findBySchedule_IdAndSessionDateIn`), else inserts | `session` (UNIQUE `schedule_id, session_date`) | A session exists; no status column |
| 2 | Publication/availability | none | none | **No publish step.** A session is live the moment it is generated. Only a rejected clinic blocks booking (`requireClinicAcceptingAppointments`). Verification gates discovery only. | — | Bookable immediately |
| 3 | Slot generation | none | none | `SlotGenerationService.generateSlotsFor`: fixed-time sessions only, one `OPEN` slot per interval (break excluded). Queue sessions get **no** pre-generated slots. | `slot` | Fixed-time: N OPEN slots. Queue: none. |
| 4 | Slot listing (patient) | `OpenSlotList` | `GET /patients/clinics/{c}/slots[?date]` | `SlotRepository.findOpenFixedTimeSlots[OnDate]`: `status = OPEN`, timed, `sessionDate >= today` | read | Includes **elapsed-today** slots and slots of **cancelled** sessions/ranges |
| 4b | Queue-session listing (patient) | `QueueSessionList` | `GET /patients/clinics/{c}/queue-sessions` | `SessionRepository.findUpcomingQueueSessionsByClinic`: `sessionDate >= today` | read | Includes cancelled sessions |
| 5 | Patient booking | `BookSlotForm` modal | `POST /patients/clinics/{c}/slots/{s}/book` | `PatientBookingService.doBookSlot`: rejected-clinic gate, protection limits, `status == OPEN`, `sessionDate >= today` (**date only**) | `booking` (partial UNIQUE active per slot), slot → BOOKED | — |
| 5b | Waitlist claim (6th booking path) | `ClaimOfferCard` | `POST /patients/waitlist-entries/{e}/claim` | `WaitlistClaimService.claim` → `PatientBookingService.bookSlot` | same | Inherits every patient-booking rule |
| 6 | Staff booking | Day-sheet "Book" link on every `OPEN` fixed-time row (`SessionSlotsView.tsx:165`) | `POST /clinics/{c}/slots/{s}/book` | `StaffBookingService`: `status == OPEN`, **no date/time check at all** | same | — |
| 7 | Queue booking | patient/staff queue forms | `POST …/sessions/{s}/queue-bookings` (both realms) | `Patient/StaffQueueBookingService` → `QueueSlotService.issueNextSlot`: mode check only; **no date check, no cancellation check** | new token slot BOOKED + booking | — |
| 8 | Walk-in | `FrontDeskWalkInPage` (lists today's sessions) | `POST /clinics/{c}/walk-ins` | `FrontDeskWalkInService.register`: rejected-clinic gate, reason; **no date or cancellation check**; `issueNextWalkInSlot` (untimed) or `issueNextSlot` (queue) | new slot BOOKED + booking + inbox item | Allowed after the session's end by design (spec 063 "Session over") |
| 9 | Whole-session cancel | `CancelSessionButton`: **client-side refuses when no slot is BOOKED** (`hasActiveBookings === false` → "nothing to cancel", `CancelSessionButton.tsx:118`) | `POST /clinics/{c}/sessions/{s}/cancel` | `SessionCancellationService.cancelSession`: BOOKED slots only → `cancelIfActive` (no waitlist event) → **slot → OPEN**; notification event; **throws `SESSION_ALREADY_CANCELLED` when no BOOKED slot exists** | booking CANCELLED, slot OPEN; **nothing records the session as cancelled** | Session fully bookable again |
| 10 | Partial cancel | `CancelFromCutoffForm` (cutoff + optional `toTime`) | `POST …/cancel-from-cutoff` | `SessionPartialCancellationService.cancelFromCutoff`: BOOKED slots with start (fixed-time) or `createdAt` (queue) in `[cutoff, toTime)`; untimed walk-ins excluded; → cancel, **slot → OPEN**; zero qualifying = success; **repeatable** | same | Range fully bookable again |
| 11 | Appointment cancel | patient/staff/batch | `…/bookings/{b}/cancel`, `…/cancel-batch` | `BookingCancellationService.doCancel` (BOOKED or APPEARED) → slot → OPEN → `BookingCancelledEvent` → `WaitlistBumpListener` → `WaitlistMatchingService.matchAndOffer` (checks only `slot.status == OPEN`) | booking CANCELLED, slot OPEN, possibly waitlist OFFERED | Slot offerable, **even inside a cancelled session/range or after its start time** |
| 12 | No-show | none (badge) | none | `NoShowDetectionService` every minute: BOOKED timed fixed-time slots, start + 10 min < now → NO_SHOW | slot NO_SHOW (booking stays ACTIVE) | Not reopened |
| 13 | Session reopening | none | none | **Not supported.** Nothing records a cancellation, so there is nothing to reopen. **Latent issue:** `DELETE /clinics/{c}/sessions/{s}` (`SessionDeletionService`, allowed only with no bookings or offers) deletes an empty session, and the next nightly run **regenerates it** for the same `(schedule, date)`. Deletion is not a durable way to take an empty session out of service. | — | — |

## 2. Domain answers

1. **Session**: one doctor's concrete working period on one date at one clinic, snapshotted from a schedule (or schedule-less since V34). It is the unit staff think of as "the doctor's clinic on Tuesday morning".
2. **Slot**: a unit of capacity *inside* a session, and the carrier of a visit's progress. It is a timed appointment slot, a queue token, or an untimed walk-in place. Its status mixes availability (OPEN) with visit state (BOOKED/APPEARED/NO_SHOW/COMPLETED).
3. **Cancellation is a session-level fact about time**: "the doctor is unavailable for [all | from–to] of this session". It cannot be a slot state alone because:
   - queue sessions have no slots until tokens are issued
   - walk-in places are created on demand
   - an empty queue session has nothing to mark

   Individual appointment cancellation stays a booking fact (`booking.status`).
4. **Existing BOOKED slots when a session is cancelled**: their bookings are cancelled exactly as today (notification, no waitlist bump). The slot may keep returning to `OPEN` (unchanged semantics); it is **not bookable** because its session or range is cancelled.
5. **Already-cancelled slots/bookings**: untouched. Cancelled bookings stay CANCELLED with their reason and time.
6. **Empty session**: cancellation must succeed, report 0 bookings cancelled, and make the session unbookable. This requires a durable record, because deletion gets regenerated (§1 row 13).
7. **Can a cancelled session become bookable again?** **Not in this design.** No reinstate path exists today. See §12, Q1.
8. **Can a partially cancelled session still contain valid future slots?** Yes. Slots before the cutoff (and at or after an optional `toTime`) stay bookable, subject to the elapsed rule.
9. **Bookability rule** (one rule, all paths): a request is accepted only if **all** of these hold:
   - clinic not rejected (existing)
   - session date ≥ today
   - timed slot: start strictly after now (BUG-005)
   - session not whole-cancelled
   - probe time not inside a cancelled range, where the probe is the slot start for timed slots, or now-on-the-session-date for tokens and walk-ins (spec 030 FR-008 proxy)
   - `slot.status == OPEN` (timed slots; existing)
   - the existing protection limits
10. **`slot.status` alone is insufficient.** Bookability = slot state **and** session state **and** time.
11. **Enforcement layer:**
    - **service layer** (authoritative): every booking path, plus waitlist offering
    - **repository queries** (the patient listings must not offer what the service would refuse)
    - **database**: the durable cancellation record, with constraints guarding its integrity
    - the **UI** only reflects this (hides actions); it is never the guard

## 3. Root cause — BUG-002 (whole-session cancel reopens slots)

- **Design gap, not a typo.** Spec 029 Clarification Q1 / FR-008 deliberately returned slots to OPEN with "no new Session-level concept".
- The schema has no place to record that a session is cancelled (V8: no status).
- Consequence: no listing or booking path can distinguish "never booked" from "cancelled". Patient/staff fixed-time booking, queue tokens and walk-ins all accept the session.
- Files: `SessionCancellationService.java`, `SlotRepository.findOpenFixedTimeSlots*`, the 5 booking services.

## 4. Root cause — BUG-003 (partial cancel reopens range)

- The same deliberate choice (spec 030 Clarification Q1).
- The `[cutoff, toTime)` range exists only as a request parameter and is never persisted.
- After the call, nothing distinguishes the cancelled range from normal availability, and repeated calls leave no trace.

## 5. Root cause — BUG-004 (empty session cannot be cancelled)

Two layers:
- **Backend:** `SessionCancellationService` uses "no BOOKED slot" as its only *already cancelled* signal, because no real signal exists. So an empty session, or one whose visits are all APPEARED/NO_SHOW, returns 409 `SESSION_ALREADY_CANCELLED`.
- **Frontend:** `CancelSessionButton` pre-empts the call when `hasActiveBookings === false`.

Deletion is not a substitute:
- It is ClinicAdmin-only.
- It is blocked once any booking exists.
- The nightly generator recreates the deleted session (§1 row 13).

## 6. Root cause — BUG-005 (elapsed same-day slots bookable)

Only the **date** is compared:
- `PatientBookingService.doBookSlot`: `sessionDate.isBefore(today)`
- the listing queries: `sessionDate >= :from`

`StaffBookingService` compares nothing at all.

Waitlist offers (`matchAndOffer`) also ignore time, so an elapsed slot can be offered.

**No schema involvement.** It is independent of V41.

## 7. Is V41 necessary?

**For BUG-002/003/004: yes.** Evidence:
- **Queue sessions** have no pre-generated slots, so a whole or partial cancellation of a queue session (backlog 026/027 require both modes) has no slot to mark. New tokens are minted on demand by `QueueSlotService`.
- **Empty queue sessions** have nothing at all to mark.
- **Walk-in places** are created on demand after the cancellation.
- **Deletion** is not durable (it is regenerated) and loses history.
- **Existing columns**: none can carry the fact without reinterpretation (`delay_minutes`, `break_*` have other meanings).

**For BUG-005: no.** It is fixed purely in application code (§9-B) and shipped separately from V41.

A **no-migration alternative** was evaluated: a new `SlotStatus.CANCELLED` stored in the existing VARCHAR, which needs no DDL. It is rejected; see §8-J.

## 8. Proposed V41 (not created)

### A. Purpose

Persist the fact that a session, or a time range of a session, has been cancelled, so every booking path and listing can refuse it durably. This includes empty and queue-mode sessions.

### B. Exact schema change

`V41__session_cancellation.sql`. Latest applied is V40 (`V40__queue_tokens_booked.sql`); no V41 exists (checked).

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `session_id` | `UUID NOT NULL REFERENCES session (id)`. Default (NO ACTION), **not** CASCADE (revised from data-model.md, see H). |
| `from_time` | `TIME NULL`. NULL = whole session. |
| `to_time` | `TIME NULL`. NULL = to the end of the session. |
| `cancelled_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` |
| `cancelled_by_account_id` | `UUID NOT NULL REFERENCES account (id)`. The staff account that performed the cancellation. This follows the booking model's actor column (`booking.booked_by_account_id`, a UUID with an FK), since this is a staff action at a clinic, not a Super Admin change-log entry. Approved decision 7. |

Constraints and indexes:
- `ck_session_cancellation_range`: CHECK (`to_time IS NULL OR (from_time IS NOT NULL AND to_time > from_time)`)
- `uq_session_cancellation_whole`: UNIQUE INDEX on `(session_id)` WHERE `from_time IS NULL`. At most one whole-session record; closes the concurrent double-cancel race at the database.
- `idx_session_cancellation_session`: INDEX on `(session_id)`, used by the listing sub-queries

Convention check:
- `V{n}__snake_case.sql` naming
- a header comment explaining purpose (as in V39/V40)
- UUID primary key via `gen_random_uuid()` (pgcrypto, enabled in V1)
- `TIMESTAMPTZ` for instants (V38 precedent)
- `TIME` for times of day (matches `session.start_time`)
- forward-only

### C. Existing data impact

- **None.** A pure `CREATE TABLE` plus constraints and indexes on an empty table.
- No existing row is read, updated or locked beyond the brief catalog lock.
- Safe at any data volume.

### D. Backfill

- **None proposed.** Historical whole/partial cancellations left no durable marker.
- Partial evidence exists only in `notification_event` rows (`BOOKING_CANCELLED_SESSION` / `BOOKING_CANCELLED_PARTIAL`, keyed by booking id). These cover only patients with accounts and carry no cutoff time. Reconstructing from them would be guesswork.
- **Effect:** sessions cancelled before V41 remain bookable until staff cancel them again. With this change, re-cancelling succeeds even when empty.

Optional, for your decision: a one-off, read-only report query listing future sessions that have `BOOKING_CANCELLED_SESSION` events, so staff can re-cancel them.

### E. Existing status values affected

None:
- `SlotStatus`, `BookingStatus`, `ScheduleMode`, `BookingCancellationReason` and every other enum are unchanged.
- No stored value is reinterpreted.
- Cancelled bookings' slots still return to `OPEN` exactly as today.

### F. Backward compatibility

- **API:** no endpoint or body shape changes. New refusals use 409 `SESSION_NOT_ACCEPTING_BOOKINGS` (new code) and 409 `SLOT_DATE_IN_THE_PAST` (existing code). An empty-session cancel changes from 409 to 200.
- **Rolling deploy:** the old application version against a V41 database works, because Hibernate `validate` checks only mapped entities and an extra table is ignored. It simply doesn't honour the records.

### G. Rollback

- Flyway is forward-only (constitution). To back out:
  1. deploy application code without the entity
  2. then add a `V42` that drops the table
- The order matters: the new code with the table missing fails Hibernate `validate` at startup.
- Rows written between deploy and rollback are lost on drop. Those sessions become bookable again, so re-cancel operationally.

### H. Application code that must change

1. **New in `scheduling`:**
   - `SessionCancellationRecord` entity
   - repository
   - `SessionAvailabilityService`:
     - verdict `ACCEPTING` / `PAST_DATE` / `ELAPSED` / `CANCELLED`
     - `recordWhole`, `recordRange`, `isWholeCancelled`
     - injectable `Clock`, following the `SessionLiveStatusService` constructor pattern
2. **`SessionCancellationService`:**
   - already whole-cancelled → 409
   - else cancel the BOOKED bookings (unchanged)
   - then insert the whole record (`saveAndFlush`; a unique violation → 409)
   - return the count, 0 allowed
3. **`SessionPartialCancellationService`:**
   - whole-cancelled → 409
   - insert a range record even when 0 bookings qualify
4. **Guard in all booking paths:**
   - `PatientBookingService`, which also covers waitlist claims
   - `StaffBookingService`
   - `PatientQueueBookingService`
   - `StaffQueueBookingService`
   - `FrontDeskWalkInService`

   Each maps the verdict to its own module's exception:
   - timed slot past or elapsed → existing `SlotDateInThePastException`
   - cancelled session, or past date for queue/walk-in → new `SessionNotAcceptingBookingsException` (409)
5. **`WaitlistMatchingService.matchAndOffer`:** skip offering a slot the rule rejects. Otherwise a staff cancellation of an APPEARED visit inside a cancelled range, or an elapsed slot, would be offered and fail at claim.
6. **`SlotRepository.findOpenFixedTimeSlots` and `…OnDate`** (plus their count queries): add `NOT EXISTS` over covering records, and `(sessionDate > :from OR startTime > :nowTime)` (the latter is BUG-005). **`SessionRepository.findUpcomingQueueSessionsByClinic`**: `NOT EXISTS` over a whole record, or a range covering `:nowTime` when `sessionDate = :from`.
7. **`SessionDeletionService.hasRealActivity`:** count cancellation records as real activity. This means:
   - single-session delete of a cancelled session is refused, instead of deleting and letting the nightly job regenerate it
   - schedule deletion detaches such sessions instead of deleting them

   Consequence: no application path deletes a session that has records, so the FK can stay NO ACTION. The database then enforces "a cancellation is never silently lost". (The earlier `data-model.md` proposed ON DELETE CASCADE; this review revises it.)
8. **Frontend (minimal):**
   - `CancelSessionButton`: remove the `hasActiveBookings === false` pre-block
   - update the success text for 0 bookings
   - optional per §12 Q3: show "Cancelled" in the day sheet and hide "Book" on cancelled rows (additive response field)

### I. Tests to change or add

See §10.

### J. Why not a slot status `CANCELLED` (or reusing OPEN/other statuses)?

1. **It can't express the fact for queue sessions, empty queue sessions, or walk-ins**, because there are no slots to mark (§7). BUG-002/004 would stay open for queue mode.
2. **It reinterprets existing consumers.** Code that assumes the 5-value slot state machine and would silently miscount a new value:
   - `SlotRepository.countBookedBySessionIdIn` counts every `status <> OPEN` as *booked* (so CANCELLED would inflate capacity counters)
   - `countStatusByClinicAndDate` feeds today-stats
   - the no-show and auto-completion scans
   - `SessionSlotsView.STATUS_LABEL` / `STATUS_BADGE_CLASS` (typed `Record<SlotStatus,…>`)
   - `QueuePositionService`
   - waitlist `matchAndOffer`
   - day-sheet mapping
3. **Visit history.** A slot is also the visit carrier. Overwriting a slot's status to CANCELLED on cancellation loses whether the cancelled slot had ever been booked or appeared, and conflicts with the fact that the *booking* already records the cancellation.
4. **The range semantics** (`toTime`, repeatable partials) would need slot rewrites on every call, plus rules for slots created later (tokens, walk-ins), which a range record answers naturally.

**Simpler schema alternative, also rejected:** columns `session.cancelled_at` plus `cancelled_from/cancelled_to`. It cannot hold repeated or disjoint ranges, which the existing API permits. It would become viable only if you decide to restrict partial cancellation to one contiguous range per session (§12 Q2).

## 9. Application changes, separated

- **A — V41-dependent (BUG-002/003/004):** §8-H items 1–5, 6 (the `NOT EXISTS` part), 7, 8.
- **B — V41-independent (BUG-005, plus the past-date part of FR-013)**, shippable first:
  - elapsed and past-date verdicts: can live in the same `SessionAvailabilityService` without the record methods, or as a small helper
  - guards in `PatientBookingService` and `StaffBookingService` (elapsed and past); queue and walk-in (past date)
  - listing `startTime > :nowTime`
  - `matchAndOffer` elapsed skip

  **Recommended order:** B first (no schema, low risk), then A with V41.

## 10. Regression-test plan

**Local-runnable (Mockito, fixed `Clock`):**
- `SessionAvailabilityServiceTest`: every verdict branch, including:
  - start == now → ELAPSED
  - range boundaries (`from` inclusive, `to` exclusive)
  - untimed on today, future and past dates
- `SessionCancellationServiceTest`:
  - whole with bookings → count + record
  - empty → 0 + record
  - APPEARED/NO_SHOW-only → success, visits untouched
  - repeat → 409, no second record
  - unique violation on concurrent insert → 409
  - after a range cancellation → whole allowed
- `SessionPartialCancellationServiceTest`:
  - range recorded with 0 qualifying
  - `toTime` recorded
  - after whole → 409
  - repeated ranges → both recorded
- `BookingPathsAvailabilityTest` (patient, staff, patient-queue, staff-queue, walk-in, waitlist offer):
  - CANCELLED → 409, nothing saved
  - PAST_DATE/ELAPSED → the right exception
  - ACCEPTING → proceeds
- `SessionDeletionService`: a cancelled session counts as real activity.
- Frontend: `CancelSessionButton` test, where an empty session reaches the API and shows "0 bookings cancelled".

**Integration (Testcontainers; must run in CI or on a Docker machine, since Docker is unavailable here):**
1. **Whole:** create a fixed-time session → book slot 1 → cancel session → session listed nowhere → patient book slot 1 → 409 → staff book slot 2 → 409 → walk-in → 409.
2. **Whole, queue:** queue session → cancel → patient token, staff token and walk-in each → 409 → absent from queue-sessions listing.
3. **Empty:** empty future session → cancel → 200 with 0 → every booking path → 409 → repeat cancel → 409 → delete → refused.
4. **Partial:**
   - setup: book slots before and after the cutoff → cancel from the cutoff
   - pre-cutoff booking stays active; post-cutoff cancelled
   - pre-cutoff OPEN slot still listed and bookable; post-cutoff OPEN not listed and 409
   - with `toTime`: slots at or after `toTime` stay bookable
5. **Partial, queue:**
   - session today, cancel range covering now → token and walk-in → 409
   - range later today → accepted
6. **Past same-day** (BUG-005, fixed clock or relative times):
   - today's elapsed slot: not listed, patient and staff 409
   - future slot today: listed and bookable
   - tomorrow: bookable
   - past date: 409 for all five paths
7. **Migration invariants:**
   - the second whole row for the same session is rejected by `uq_session_cancellation_whole`
   - `to_time <= from_time` is rejected by the CHECK
8. **Waitlist:** a cancellation inside a cancelled range, or on an elapsed slot, produces no offer.

**Existing tests to update**, each reversal justified by spec 065:
- `SessionCancellationRejectionTest.sessionThatNeverHadAnyBookingIsAlsoRejected` (encodes BUG-004)
- signature updates in `WalkInLineLifecycleTest` and `UntimedSlotSweepsTest` (listing parameters)
- `SessionCancellationConcurrencyTest`: keep it; it should still yield exactly one success
- the `SlotStatus.OPEN` assertions in `SessionCancellationSuccessTest` and `PartialSessionCancellation*` **stay valid** (slot semantics are unchanged), a deliberate property of this design

## 11. Risks

| Risk | Mitigation |
|---|---|
| Integration tests (the only proof of JPQL semantics and the DB constraints) can't run locally | Unit tests for every service branch. Startup validates the JPQL syntax and the Hibernate mapping. Run the integration suite in CI before merge. |
| Pre-V41 cancelled sessions stay bookable | Documented. Staff re-cancel. Optional report (§8-D). |
| Staff UI still shows "Open"/"Book" on cancelled rows unless §12 Q3 is approved | The booking attempt is refused with a clear message. Recommend the minimal additive day-sheet flag. |
| Listing queries gain a correlated `NOT EXISTS` | Indexed on `session_id`. The table is tiny (one row per cancellation action). |
| JVM time zone | Unchanged single-zone behaviour. A `Clock` is used for determinism (PB-005 decision pending). |
| The waitlist offer path is an extra consumer beyond the original task list | Included (§8-H item 5), since it can otherwise offer unbookable slots |
| Blocking deletion of cancelled sessions changes the deletion UX | Clear refusal message. Schedule deletion detaches instead, which is existing behaviour for sessions with activity. |

## 12. Decisions needed from the product owner

1. **Reinstatement:** may a cancelled session or range ever be reinstated? Proposed: **no** in this phase. If yes later, add a "reinstated" record, never a delete.
2. **Repeated or disjoint partial cancellations:** keep the current API behaviour (unbounded ranges, table design, recommended), or restrict to one contiguous range per session (would allow the simpler column design)?
3. **Staff visibility:** include the minimal additive day-sheet and session-list indicator plus hiding "Book" on cancelled rows in this fix (recommended)? Or leave the UI unchanged and rely on the 409 message?
4. **Historic cancellations:** no backfill (recommended), or produce the read-only report for staff to re-cancel?
5. **"Exactly now" boundary for BUG-005:** a slot starting at the current second counts as elapsed (proposed). Or allow booking until start + N minutes? The no-show grace is 10 minutes.
6. **Past-date cancellations:** cancelling a past session remains allowed (unchanged)? Proposed: unchanged.
