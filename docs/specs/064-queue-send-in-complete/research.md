# Research: Send-In and Complete for Queue Sessions (064)

All findings come from reading the code on 2026-09-24.

## Decision 1 — Tokens are minted as BOOKED at the single issuance point

**Decision**: `QueueSlotService`'s shared issuance core creates the token Slot with status `BOOKED` (waiting) instead of `OPEN`. That covers FR-001 for all three booking paths at once:
- patient self-service (`PatientQueueBookingService`);
- staff (`StaffQueueBookingService`);
- the 063 front desk (`FrontDeskWalkInService`, both modes; its explicit `setStatus(BOOKED)` becomes redundant and is removed).

**Rationale**: The queue booking services deliberately run outside a transaction and hold a detached Slot (their own documented convergence fix). Setting the status at mint time avoids re-saving a detached entity in three places.

- **Failure window**: a token minted but whose booking insert then fails. The prior step order is fee → appointment type → patient → token → booking, so every validation failure happens before the token exists. The remaining failure is an unexpected insert error on a brand-new slot, the same window that already existed for an OPEN orphan token.

**Alternatives considered**: Setting `BOOKED` in each booking service — rejected: three edits, and each would need a slot save on a detached entity.

## Decision 2 — Remove the Fixed-Time-only guard from Appeared, Complete and staff cancel

**Decision**: Drop the `mode != FIXED_TIME` refusal from:
- `SlotAppearedService.markAppeared` (FR-002);
- `SlotCompletionService.completeSlot` (FR-003). Its "not yet started" check is already skipped for untimed slots (063).
- `BookingCancellationService.doCancel`, so staff "Remove" and batch cancel work on queue bookings (FR-007/FR-009).

**Kept as they are**:
- `PatientBookingCancellationController` keeps its own Fixed-Time check, so patients still can't self-cancel a queue booking (spec edge case).
- `WaitlistBumpListener` already returns early for non-Fixed-Time sessions and untimed slots (063), so a freed token is never offered to the waitlist.
- `SessionDelayService.recalculate` is a no-op for Queue sessions.

**Found gap**: today no staff path can cancel an individual queue booking at all (`BookingCancellationService` refuses non-Fixed-Time). This also broke the 063 front-desk panel's "Remove" for queue walk-ins, which 063 never offered because its panel was Fixed-Time only.

## Decision 3 — Queue position counts only waiting tokens ahead; no position once not waiting

**Decision**: `QueuePositionService.positionOf` already counts `BOOKED` tokens with a lower number. With Decision 1, that is exactly "still waiting ahead" (FR-004): In with doctor (`APPEARED`), `COMPLETED` and cancelled (`OPEN`) tokens don't count. The own-token check widens from COMPLETED/NO_SHOW to "not BOOKED" → not applicable (FR-005), so a patient who is in with the doctor, finished or cancelled sees no position.

## Decision 4 — Front-desk counts and panel include Queue sessions

**Decision**:
- `SlotRepository.countWalkInLineBySessionIdIn` drops its `mode = FIXED_TIME` filter. Its expressions are already mode-independent:
  - "untimed BOOKED" = waiting walk-ins in a Fixed-Time session, and waiting tokens (booked or walk-in) in a Queue session;
  - "any APPEARED" = someone is in with the doctor.
- Frontend: `SessionStep` shows the waiting count and the free/busy hint for Queue sessions too. The page shows `WalkInLinePanel` for Queue sessions as well. The panel lists every waiting untimed token that has a booking (booked or walk-in, walk-ins badged), labelled "Token n" for Queue sessions and "Wn" for Fixed-Time.

## Decision 5 — One-off data migration

**Decision**: `V40__queue_tokens_booked.sql` sets `slot.status = 'BOOKED'` for Queue-session slots with status `OPEN` that have an `ACTIVE` booking (FR-011). Cancelled and completed bookings are untouched. Forward-only; a test proves an active queue booking's token counts as waiting afterwards (Constitution I).

## Unchanged by construction

- **The no-show and auto-complete sweeps** filter `startTime IS NOT NULL` (063).
- **The 008/037/062 "pending" queries** treat untimed `BOOKED`/`OPEN` slots as pending only for today or later (063 T039).
- **Day Sheet queue rows** already show Appeared/Complete by status alone (`hasSlotStarted` returns true for untimed slots).
- **The 061 live status** stays not-applicable for Queue sessions.
