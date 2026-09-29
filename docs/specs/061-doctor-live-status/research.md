# Research: Doctor Live Schedule Status

All Technical Context fields in plan.md were resolved directly from the codebase investigation performed during `/speckit-specify` and confirmed during `/speckit-clarify` — no `NEEDS CLARIFICATION` markers remained to research. This document instead records the design decisions made while turning the spec's Business Rules into a concrete backend/frontend shape, in the Decision/Rationale/Alternatives format this project's other `research.md` files use.

## Decision 1: Progression comparison, not elapsed-time comparison

**Decision**: Compute status by comparing two *slot pointers* (BR-006 actual, BR-007 expected) derived from `Slot.status` + `Slot.startTime`, never a raw `now − firstSlotTime` duration.

**Rationale**: The product owner explicitly ruled out the naive calculation, and for good reason — it can't express "running early" (a duration can't be negative-but-meaningful without an arbitrary sign convention) and it can't stay accurate through breaks or cancellations without special-casing them after the fact. Pointer comparison handles both for free: a break has no slot rows (so it's simply skipped while walking the expected pointer forward), and a cancelled slot reverts to `OPEN` and is excluded from both pointers by BR-005's participation rule — no break-aware or cancellation-aware branch is ever needed in the calculation itself.

**Alternatives considered**:
- *Naive `now − firstSlotTime`*: rejected per explicit product-owner instruction; also can't represent "running early" or "not started" correctly.
- *Extending `SessionDelayService.recalculate`'s existing algorithm* (earliest still-open/booked past-due slot) to also emit an early/not-started/completed status: rejected — that algorithm has no concept of an "expected" pointer at all, only "how late is the earliest overdue slot," so it structurally cannot detect "running early" (nothing is ever overdue when ahead of schedule) without becoming a different algorithm in practice. Keeping it as a distinct calculation (Decision 2) is clearer than bending the old one until it does two jobs.

## Decision 2: A new `SessionLiveStatusService`, not a rewritten `SessionDelayService`

**Decision**: Add `com.cms.scheduling.service.SessionLiveStatusService` as a new, pure-read, no-side-effect service. `SessionDelayService` (trigger-based, cached `Session.delayMinutes`) is left exactly as it is.

**Rationale**: The spec's own regression requirement ("confirm the existing SessionDelay* test suite still passes unchanged") is easiest to guarantee by construction when the old and new calculations are different classes — there's no shared mutable state or shared method to accidentally change. This also matches an established precedent in the same module: `SlotAppearedService`, `SlotCompletionService`, and `SlotAutoCompletionService` are three separate classes despite all mutating `Slot.status`, because each represents a distinct trigger/behavior. The scoping check (clinic membership + doctor-self-scoping) is genuinely shared logic between `SessionDelayService.currentDelay` and the new service, though — see Decision 3.

**Alternatives considered**:
- *Add a `liveStatus(...)` method directly to `SessionDelayService`*: rejected — couples an always-fresh read to a class whose entire existing contract (and tests) assumes a cached, trigger-recalculated value; a future change to one calculation risks an accidental behavior change to the other simply by proximity.
- *Replace `SessionDelayService` entirely*: explicitly rejected by the spec itself (A6) — other consumers of the existing `/delay` endpoint and `Session.delayMinutes` must keep working unchanged.

## Decision 3: Shared scoping, not shared computation

**Decision**: Factor the existing clinic-scoping + doctor-self-scoping check (`SessionDelayService.currentDelay` lines 94–114 today) out into a small shared helper both `SessionDelayService` and the new `SessionLiveStatusService` call, rather than duplicating those ~15 lines verbatim in the new class.

**Rationale**: This check is exactly the pattern FR-012 requires every new endpoint to reuse, and it's already been the source of two real security bugs in this codebase's history (a cross-clinic leak and a cross-doctor leak, both referenced in `SessionDelayService`'s own javadoc) — duplicating it invites a third instance of the same bug class drifting out of sync between two copies. This is a narrow, mechanical extraction (same signature shape, same exceptions thrown), not a new abstraction layer, so it stays within Constitution II's simplicity bar.

**Alternatives considered**:
- *Duplicate the check in the new service*: rejected given the security-bug history above — not worth the risk to save one small extraction.
- *Build a generic `@SessionScoped`-style annotation/AOP mechanism*: rejected as speculative over-engineering for two call sites (Constitution II, YAGNI).

## Decision 4: `OperationalDayService` as a single, narrow, stateless function

**Decision**: `com.cms.scheduling.service.OperationalDayService` with one method, `LocalDate operationalDateOf(LocalDateTime instant)`, implementing BR-011 (04:30 boundary) exactly once. Both the new endpoints' "Operational Day" field and the "first slot of the operational day" reference point (used only for the dashboard display, since BR-014 clarifies the first-slot reference is really just the session's own earliest slot) call this one function.

**Rationale**: BR-012 requires exactly one implementation of the 04:30 rule; a small dedicated service (rather than a static utility method) matches this codebase's existing convention of testable, injectable `@Service` beans for business rules (e.g. `NoShowDetectionService`'s grace-period constant lives in a service, not a static helper), and makes it trivial to unit-test in isolation with constructed `LocalDateTime` instants (per spec's Testing Strategy) without needing a Spring context.

**Alternatives considered**:
- *A static utility method*: rejected only for consistency with this codebase's existing preference for testable service beans over static helpers in the `scheduling` module — not a strong technical objection, but keeping the pattern consistent avoids introducing two different styles for "one centralized function" in the same module.
- *Applying the 04:30 boundary inside existing services* (`NoShowDetectionService`, nightly session generation, Day Sheet queries): explicitly rejected per the 2026-09-23 clarification (A3) — out of scope for this spec.

## Decision 5: Endpoint placement — extend existing controllers, add one new one

**Decision**: Add one new `@GetMapping` method to the existing `SessionDelayController` (`GET /api/v1/clinics/{clinicId}/sessions/{sessionId}/live-status`, alongside the untouched existing `/delay` route) for the staff/doctor view. Add one new controller, `PatientSessionLiveStatusController` (`GET /api/v1/patients/bookings/{bookingId}/live-status`), mirroring `PatientQueuePositionController` exactly (booking-ownership filter, `BookingNotFoundException` on mismatch, `SecurityConfig.currentPatientAccountId(authentication)`), for the patient view.

**Rationale**: This is literally "enhance the existing feature" for the staff/doctor side (same controller, sibling route, existing scoping helper via Decision 3) — no new controller class needed there. The patient side has no existing controller to extend (spec's own investigation confirmed no patient-facing live view exists today), so a new controller is unavoidable, but it is a mechanical copy of `PatientQueuePositionController`'s already-proven shape, not a new pattern.

**Alternatives considered**:
- *One combined endpoint serving both staff and patient audiences by role-branching inside a single controller method*: rejected — the two audiences have different ownership checks (clinic/doctor-staffing vs. booking-ownership) and different response shapes (FR-004/FR-011 patient privacy trimming), and this codebase's own `QueuePositionResponse`/staff-vs-patient controller split is the direct precedent for keeping them separate.

## Decision 6: Frontend — one new indicator component, mirroring `QueuePositionIndicator`

**Decision**: `frontend/src/features/session-delay/LiveScheduleStatusIndicator.tsx`, same `{ mode: 'staff'; clinicId; sessionId; refreshKey? } | { mode: 'patient'; bookingId; refreshKey? }` discriminated-union prop shape as `QueuePositionIndicator`, same `loading | error | not-applicable | ready` status model, same ~20s `setInterval` polling (confirmed cadence, clarification A4). `session-delay/api.ts` gains two client functions (`getLiveScheduleStatusAsStaff`, `getLiveScheduleStatusAsPatient`) calling the two new endpoints from Decision 5.

**Rationale**: FR-005–FR-007 require reusing the existing polling pattern exactly, and `QueuePositionIndicator` is that pattern's only existing implementation — copying its shape (not just its polling technique) also gets the already-solved "loading vs. temporarily-failed vs. not-applicable" distinction FR-007 asks for, for free, instead of re-deriving it.

**Alternatives considered**:
- *Enhance `DelayIndicator.tsx` in place*: considered, but `DelayIndicator` currently has no concept of "not applicable" vs "error" (it collapses both to rendering nothing) and no patient mode at all — reshaping it to do both jobs risks the same coupling problem as Decision 2's backend equivalent. `DelayIndicator` is left as-is (A6); `SessionOperationsPanel` wires in the new component alongside/instead of it at implementation time (an implementation detail for `/speckit-tasks`, not fixed here).
