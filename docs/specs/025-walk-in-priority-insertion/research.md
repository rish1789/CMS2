# Research: Walk-In / Priority Insertion

## R1: Where does the priority search live, and how is it evaluated?

**Decision**: A new `WalkInInsertionService` (new class, `com.cms.booking` module) loads all
`Slot`s for the target `Session` via the existing `SlotRepository.findBySession_Id(sessionId)`,
sorts them by `startTime` ascending in Java (this repository method carries no `ORDER BY`, so
relying on incidental row order would make the tier-internal tie-break undefined — analyze
finding E2), then evaluates the three priority tiers in order against that sorted list:
1. first `Slot` with `isBuffer() == true && getStatus() == OPEN`
2. else first `Slot` with `getStatus() == NO_SHOW`
3. else first `Slot` with `isBuffer() == false && getStatus() == OPEN` — only if an override
   reason was supplied
4. else reject as "nothing available"

When more than one Slot qualifies within the same tier (e.g. two OPEN buffer Slots), the
earliest-`startTime` one is used — a deterministic, spec-consistent default (staff would expect
the soonest available slot, not an arbitrary one) rather than leaving it to incidental row order.

**Rationale**: A Session's Slot count is small (bounded by one clinic-day's schedule — the same
scale `NoShowDetectionService.findBookedFixedTimeCandidatesForNoShow()` already treats as
Java-side-filterable, per that method's own documented reasoning), so no new indexed query is
needed beyond the existing `findBySession_Id`. Keeping tier selection in Java (not three separate
repository queries) makes the strict-order guarantee (SC-001) a single, directly-readable method
instead of three round trips whose ordering could drift apart from the spec's own ordering.

**Alternatives considered**: Three separate `SlotRepository` queries (one per tier), called in
sequence, short-circuiting on the first non-empty result. Rejected: no performance benefit at this
scale, and it fragments a single business rule (the priority order itself) across three query
method names instead of one readable method — harder to verify by inspection that the order is
actually adhered to (Constitution I's testability, and this session's own precedent — 014's
non-retroactivity being verified "by grep" — of preferring a structurally-obvious implementation).

## R2: How does a priority-(2) insertion reuse the NO_SHOW Slot, per the Clarifications answer?

**Decision**: `WalkInInsertionService` calls `BookingRepository.findBySlot_Id(slot.getId())` to
find the existing no-show `Booking`, resolves the fee and validates everything else the same way
first (see R3 for ordering), then — as the last write step, immediately before creating the new
`Booking` — deletes the old one via `BookingRepository.delete(oldBooking)` **followed by an
explicit `bookingRepository.flush()`**, and only then creates the walk-in `Booking` against the
same `Slot` object, resetting `Slot.status` to `BOOKED`.

**Implementation-time finding**: the explicit flush immediately after the delete is load-bearing,
not decorative. Hibernate's default flush ordering runs pending insertions *before* pending
deletions within a single flush — if the delete were left unflushed and only the final
`saveAndFlush(newBooking)` triggered a flush, the new Booking's INSERT would execute first and
collide with `uq_booking_slot` against the still-present old row, exactly backwards from the
intended delete-then-insert sequence. Flushing the delete on its own forces it to actually run
against the database before the new Booking is ever constructed.

**Rationale**: FR-001a requires the delete-and-replace to be a single atomic outcome, and requires
the original Booking to survive untouched if anything upstream fails. Since `Booking.slot_id` has
a `uq_booking_slot` constraint (one Booking per Slot, ever), the old Booking's row must actually be
gone before the new one can be inserted for that same `slot_id` — there is no way to have both
rows exist simultaneously even transiently within one transaction's constraint checking at flush
time. Placing the delete last, after every other validation/resolution has already succeeded,
satisfies FR-001a's "only removed without a successful replacement" requirement: if fee resolution
throws, the method returns via exception before `delete()` is ever called, and the transaction
rolls back nothing because nothing was written.

**Alternatives considered**: Soft-delete/status flag on the old Booking instead of a hard delete.
Rejected — over-engineering for a requirement the Clarifications answer explicitly settled as "delete
the original no-show Booking first"; no feature in this backlog needs a no-show Booking's history
preserved after it's reclaimed, and the Clarifications answer itself calls out this is a deliberate,
accepted departure from the codebase's usual append-only bias, not an oversight to work around.

## R3: Write-ordering — fee resolution, patient resolution, and the old-Booking delete

**Decision**: Mirror 020's `StaffBookingService.bookSlot` write-gate ordering exactly, with the
no-show delete inserted as the very last step before the new Booking is persisted:
1. Load Session (404 if missing / wrong clinic) and evaluate the priority tiers (R1) → determines
   target Slot and whether an override reason is required.
2. Authorize caller (Operations or ClinicAdmin at this clinic — mirrors 020/022's `requireAuthorized`).
3. If tier 3 was selected and no override reason was supplied → reject before anything else runs.
4. Resolve and lock the fee (`FeeResolutionService.resolve`) — the first real write-gate, exactly
   as 020/022 already establish as this codebase's pattern.
5. Resolve-or-create the walk-in Patient (duplicated helper, same shape as 020's/022's own
   `resolveOrCreatePatient` — see R4).
6. If tier 2 (no-show-freed slot): delete the old Booking (`bookingRepository.delete`).
7. Save the new `Booking` via `saveAndFlush` (020's convergence-fixed pattern — forces the
   `uq_booking_slot` constraint check synchronously here, inside a catchable scope) and set
   `Slot.status = BOOKED`.

**Rationale**: Steps 1–3 are pure reads/validation (no writes possible yet, so ordering among them
is free); placing fee resolution before Patient creation exactly matches 020/022's already-proven
"nothing written before the fee resolves" guarantee (FR-005). The old-Booking delete is placed
after fee/patient resolution specifically so a failure in either of those never touches the old
Booking (FR-001a) — it only runs once every other precondition for a *successful* insertion has
already been satisfied.

**Alternatives considered**: Delete the old Booking first (mirroring "clear the target before
booking it"). Rejected — directly violates FR-001a's explicit requirement that a failed insertion
attempt must leave the original no-show Booking untouched; deleting first and then failing on fee
resolution would destroy Booking history for no successful replacement.

## R4: Patient resolution — reuse or duplicate?

**Decision**: Duplicate a private `resolveOrCreatePatient` helper inside `WalkInInsertionService`,
identical in shape to `StaffBookingService`'s and `StaffQueueBookingService`'s own private methods
of the same name (existing-Patient lookup by ID scoped to clinic, or validate-and-create a new
walk-in `Patient` with a null `patientAccount`).

**Rationale**: This is already this codebase's established precedent — both 020's and 022's staff
booking services independently duplicate the identical small helper rather than sharing an
abstraction, consistent with Constitution II (Simplicity & YAGNI: no abstraction layer for a need
that's only ever been "three call sites of an eight-line method"). Introducing a shared utility
now would be the first cross-service abstraction of its kind in `com.cms.booking`, unjustified by
any current requirement beyond "avoid typing the same eight lines a third time."

**Alternatives considered**: Extract a shared `PatientResolutionHelper`/static method. Rejected per
Constitution II and the documented codebase precedent above — no third requirement has emerged
that needs the abstraction to vary except by copy-paste convenience, which the constitution
explicitly says is not sufficient justification on its own.

## R5: Endpoint shape — target a Session, not a specific Slot

**Decision**: `POST /api/v1/clinics/{clinicId}/sessions/{sessionId}/walk-in`, request body carries
patient fields (existing-patient-id or name+phone), `appointmentTypeId`, and an optional
`overrideReason`. Response reuses the existing `BookingResponse` DTO (020's), since a walk-in
insertion produces exactly the same shape of result (a confirmed Booking with a locked fee) — no
new response fields are needed (unlike 022's `QueueBookingResponse`, which needed a token number
the source Booking doesn't carry).

**Rationale**: Unlike 020's staff booking (where staff already know the specific Slot they're
targeting, from a slot-list view), this feature's entire point is that staff do *not* pick the
Slot — the system selects it via the priority order (FR-001). The natural target of the request is
therefore the Session, not a Slot ID staff can't be expected to already know among buffer/no-show/
regular candidates.

**Alternatives considered**: `POST /api/v1/clinics/{clinicId}/slots/{slotId}/walk-in` (staff pick a
specific Slot from a filtered list, override reason required for non-buffer/non-no-show ones).
Rejected — this would push priority-order enforcement onto the client (staff would need to already
know which Slot is highest-priority to pick correctly), directly undermining the feature's own
stated purpose ("without staff having to guess which slot is safe to give away").

## R6: Security chain

**Decision**: Add one new matcher to the existing `/api/v1/clinics/**` chain in
`com.cms.identity.account.SecurityConfig` (the same chain 016/020's staff booking, 013's schedule
endpoints, etc. already sit under) — no new `SecurityFilterChain`.

**Rationale**: The new endpoint's path (`/api/v1/clinics/{clinicId}/sessions/{sessionId}/walk-in`)
falls under the existing `/api/v1/clinics/**` prefix that chain already secures end-to-end with
staff-JWT auth; 014's own converge-pass explicitly flagged and fixed the exact bug class of a new
path segment silently falling through to `permitAll()` when it isn't added to this chain's explicit
matcher list — this plan proactively adds the matcher up front rather than repeating that mistake.

**Alternatives considered**: A new dedicated `SecurityFilterChain` (mirroring `BookingSecurityConfig`'s
`@Order(6)` for doctor-scoped paths). Rejected — this endpoint is clinic-scoped, not doctor-scoped,
so it belongs under the same chain as every other clinic-scoped booking endpoint; a new chain would
be an unjustified duplication of matcher/JWT-filter wiring for a path that already fits an existing
chain's prefix.

## R7: `override_reason` column

**Decision**: New nullable `override_reason` (text) column on `booking`, added via a new Flyway
migration (`V14__booking_override_reason.sql`) — null for a tier-1/2 insertion, non-blank for a
tier-3 one.

**Rationale**: FR-004 requires the override reason to remain retrievable as part of the resulting
record; `Booking` is that record. Nullable (not a separate table) because it's a 1:0..1 relationship
intrinsic to the Booking itself, not a repeating/multi-valued concept — no future feature in this
39-item backlog needs more than one reason per Booking.

**Alternatives considered**: A separate `walk_in_override` table keyed by `booking_id`. Rejected as
unjustified complexity (Constitution II) for a single optional text field with no independent
lifecycle of its own.
