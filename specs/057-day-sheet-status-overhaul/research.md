# Phase 0 Research: Day Sheet Smart Status Flow

No `NEEDS CLARIFICATION` markers remain in the Technical Context (this is a same-stack extension of existing, well-established patterns — no new language/framework/storage decision is needed). This document instead records the concrete design decisions this plan depends on, each grounded in the actual current code, not assumption.

## Decision 1: `APPEARED` is a new `SlotStatus` enum value, not a separate table/flag

**Decision**: Add `APPEARED` to `com.cms.scheduling.domain.SlotStatus` (currently `OPEN, BOOKED, NO_SHOW, COMPLETED`). No Flyway migration at all.

**Rationale**: `slot.status` is a plain `VARCHAR(20)` column with no check constraint (confirmed against `V10__create_slot.sql`), and `V13__slot_no_show_and_hold_support.sql`'s own comment already establishes the precedent explicitly: "NO_SHOW itself is a new SlotStatus enum constant, persisted as a string - it needs no schema change of its own." Adding `APPEARED` is exactly the same case — a pure Java-side enum addition, zero data migration for existing rows (every existing slot is already one of the four current values; none needs backfilling).

**Alternatives considered**:
- A separate `appeared_at` timestamp column alongside the existing status, leaving `SlotStatus` itself untouched: rejected because the spec's own resolved Clarifications treat Appeared as a real state with its own eligibility rules (removes No-Show eligibility, gates auto-completion) — modeling it as a side timestamp would mean every consumer of slot state (No-Show sweep, completion sweep, cancellation eligibility, Day Sheet display) has to check two fields instead of one, adding incidental complexity Principle II asks to avoid.

## Decision 2: The auto-completion sweep mirrors `NoShowDetectionService`/`NoShowDetectionTrigger` exactly, including its non-`@Transactional`-outer shape

**Decision**: New `SlotAutoCompletionService.completeExpiredAppearedSlots()` (no class-level `@Transactional`, per-candidate save, returns a count) plus `SlotAutoCompletionTrigger` (`@Scheduled(cron = "0 * * * * *")`), structurally identical to the existing No-Show sweep.

**Rationale**: `NoShowDetectionService`'s own Javadoc explains *why* it is deliberately not `@Transactional` at the class level and calls `slotRepository.save(...)` directly per candidate: that exact self-invocation `@Transactional`-bypass shape already caused two real bugs in this codebase's history (020, 022's first attempt). Building a second sweep with a different transaction shape would reintroduce the exact risk this codebase already paid to learn to avoid. The per-minute cadence is reused for the same reason 023 chose it: a nightly cadence (015's) would be far too infrequent relative to slot-length granularity.

**Auto-completion effects**: reuses the same effect a manual completion has today — `SessionDelayService.recalculate(sessionId)` — via a new `SlotCompletionService.completeSlotAutomatically(Slot)` method that performs the status write + recalculation with **no authorization check** (the sweep is a system action, not a user action, exactly like `NoShowDetectionService` has none). The existing authorized `completeSlot(callerAccountId, clinicId, slotId)` entry point is unchanged in shape; it now additionally accepts the treating doctor (Decision 5), with role-dependent eligibility: ClinicAdmin/Operations keep the existing `BOOKED` path unchanged and additionally gain `APPEARED`; the treating doctor is only ever eligible from `APPEARED` (see contracts/day-sheet-status-flow.md and data-model.md — this is additive, not a narrowing of ClinicAdmin/Operations' existing access, per FR-008).

**Alternatives considered**: A single merged "status sweep" service handling both No-Show and auto-completion in one pass: rejected — they run on disjoint candidate sets (`BOOKED` vs `APPEARED`) with different downstream effects, and merging them would make the No-Show sweep's already-reviewed, already-tested logic harder to reason about for a saving that's purely cosmetic (one cron entry instead of two costs nothing at this scale).

## Decision 3: No-Show correction and the original "mark Appeared" action are the same endpoint

**Decision**: One `SlotAppearedService.markAppeared(callerAccountId, clinicId, slotId)`, accepting a slot currently in `BOOKED` **or** `NO_SHOW`, transitioning it to `APPEARED`. No separate "undo No-Show" action.

**Rationale**: Per the spec's resolved Clarification (Q1-A), a No-Show is correctable, and the corrected state's target and downstream behavior (follows the normal Appeared → auto-Completed flow) is identical regardless of which status the slot was in before. Two endpoints for the same effect would be unjustified complexity under Principle II.

## Decision 4: Cancellation eligibility is extended, not duplicated

**Decision**: `BookingCancellationService.doCancel`'s existing guard —

```java
if (slot.getStatus() != SlotStatus.BOOKED) {
    throw new BookingNotCancellableException(booking.getId());
}
```

— becomes `if (slot.getStatus() != SlotStatus.BOOKED && slot.getStatus() != SlotStatus.APPEARED)`. No new cancellation service, no new business rule; the already-tested `cancelIfActive` race-guard, `BookingCancelledEvent` publication (which is what a waitlist offer already listens for), and both existing callers (`PatientBookingCancellationController`, `StaffBookingCancellationController`) are unchanged and unaffected — a Booked slot's cancellation behavior is byte-for-byte identical to today.

**Rationale**: FR-012 explicitly requires the new batch-cancel path to reuse "the same rules and side effects" as today's single cancellation, and FR-009 explicitly makes Appeared slots selectable for cancellation. The only correct way to satisfy both without duplicating the concurrency-sensitive `cancelIfActive` logic (which this class's own Javadoc warns has already caused a real detached-entity bug once) is to widen this one guard.

**Test impact**: `BookingCancellationService`'s existing unit/contract/integration tests that assert a non-`BOOKED` slot is rejected must be checked — none currently assert `APPEARED` specifically (it doesn't exist yet), so no existing test's expected outcome changes; a new test asserting an `APPEARED` slot **can** be cancelled is added, alongside the existing "an `OPEN`/`NO_SHOW`/`COMPLETED` slot cannot" cases, which remain correct unchanged.

## Decision 5: Doctor completion authorization is added locally inside `scheduling`, not reused from `clinical.TreatingDoctorAuthorizationService`

**Decision**: `SlotCompletionService.requireAuthorized` gains a third allowed case — the caller is the treating doctor for that slot's session (`slot.getSession().getDoctorProfile().getAccount().getId().equals(callerAccountId)`) — implemented directly in `scheduling`, not by calling into `com.cms.clinical.service.TreatingDoctorAuthorizationService`.

**Rationale**: `clinical` already depends on `booking`, which already depends on `scheduling`. Calling from `scheduling` into `clinical` would create a dependency cycle across module boundaries, which Constitution III's "clear boundaries" requirement rules out. `scheduling` already directly owns the `Slot → Session → DoctorProfile` relationship the check needs (it's the same shape `TreatingDoctorAuthorizationService` uses, just reached from the correct side of the boundary) — no cross-module call is actually necessary. This is the same "narrow, purpose-built, locally-owned check" precedent this codebase already used for `SlotCompletionService.requireAuthorized` itself.

**Alternatives considered**: Extracting a shared `common`-module authorization helper both `clinical` and `scheduling` call: rejected as unjustified complexity for a two-line, entity-shape check with no other required behavior in common — Principle II.

## Decision 6: Frontend role visibility is plumbed through `ClinicShell`'s already-resolved role via React Router's `Outlet` context

**Decision**: `ClinicShell.tsx` (which already resolves the caller's `StaffRole` for its own sidebar, via `listMyClinics`) passes `{ role }` through `<Outlet context={{ role }} />`; `SessionSlotsView.tsx` and the new Appeared/batch-cancel UI read it via `useOutletContext<{ role?: StaffRole }>()`.

**Rationale**: Confirmed in code that `ClinicShell` already fetches and holds `role` in state today, but currently only uses it for `Sidebar`'s `activeRole` prop — it is not currently passed to routed child pages at all, which is why `SessionSlotsView.tsx` today has zero role-conditional rendering anywhere (every action is shown to every role, relying entirely on the backend to 403 an unauthorized doctor click). `Outlet` context is React Router's own built-in mechanism for exactly this (`ClinicShell` already imports and renders `<Outlet>`), so no new state-management dependency is introduced.

**Scope of the change**: this is the **first** real per-page, per-role UI gating in this codebase (today's only role gating is the sidebar's nav-item filter and backend-side 403s). It's introduced narrowly for this feature's named requirements (FR-007, FR-015) — not generalized into a broader "permissions framework," per Principle II.

## Decision 7: The batch-cancel endpoint iterates the existing single-cancel service; it does not introduce a new cancellation code path

**Decision**: `POST /api/v1/clinics/{clinicId}/sessions/{sessionId}/bookings/cancel-batch` accepts a list of `bookingId`s, checks ClinicAdmin/Operations authorization once, then calls `BookingCancellationService.cancel(Booking)` per booking (each already its own `Propagation.REQUIRES_NEW` transaction), collecting per-booking success/failure into the response rather than failing the whole batch on one loss.

**Rationale**: `BookingCancellationService`'s own Javadoc already documents that `REQUIRES_NEW` was specifically chosen so "a lost race here (a normal, expected outcome for one booking in a large batch)... [doesn't mark] the cascade's entire shared transaction rollback-only" — this is precisely the batch scenario this feature needs, already built and already proven by the de-verification cascade (033) using the same service the same way. FR-014's "report that specific failure without... blocking the slots that could still be cancelled" is exactly this existing design's documented behavior.

**Authorization is intentionally narrower here than the existing single-cancel endpoint**: `StaffBookingCancellationController` today allows "any active role" (including Doctor) to cancel one booking. Per the spec's resolved Clarification (Q3-A), the new batch-cancel UI/endpoint is ClinicAdmin/Operations-only. This plan does **not** retroactively tighten the existing single-cancel endpoint — that endpoint and its current doctor access are outside this feature's named scope (FR-015 names "the cancel-selection UI" specifically), and silently changing already-shipped, already-tested behavior beyond what was asked would itself be a Governance violation ("no silent deviation").

**Alternatives considered**: A single "cancel everything eligible on this day" endpoint mirroring feature 026's whole-day cancellation: explicitly rejected by the spec's resolved Clarification (Q2-A) — "select all" is a UI convenience over the per-slot rule, not an invocation of the separate whole-day-cancellation feature.
