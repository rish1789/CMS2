# Research: Staff-Assisted Fixed-Time Booking

## Decision: Fee resolution is the first side-effecting call inside one `@Transactional` `bookSlot` method

**Rationale**: The cleanest way to guarantee "if no fee can be resolved, nothing is created" (FR-004, spec Scope Decisions) is structural, not a manual cleanup/compensation step: call `feeResolutionService.resolve(...)` before touching `Patient`, `Slot`, or `Booking` at all. If it throws, the method returns via exception, the surrounding `@Transactional` boundary rolls back automatically (nothing was written yet to roll back, but critically nothing partial can ever exist even if a future edit to this method's ordering accidentally introduced an earlier write — the transaction boundary is the actual backstop, the ordering is the design intent).

**Alternatives considered**: Resolving the fee last (after Patient/Slot/Booking are tentatively built in memory) and only then deciding whether to persist — rejected; it's the same outcome via more roundabout code, and puts the "have we already done something we need to undo" question back in the reader's head instead of making it structurally impossible.

## Decision: A new walk-in `Patient` is created via its existing constructor directly (`new Patient(clinic, null, name, phone)`), not through 009's `PatientLinkingService`

**Rationale**: `PatientLinkingService.findOrCreatePatient` (009) is keyed on an existing `PatientAccount` id — its entire contract assumes the caller already has a logged-in patient identity to find-or-link against. A staff-entered walk-in patient with no account has no such id to supply. `Patient`'s own constructor already accepts a `null` `patientAccount` directly (009's own data model already anticipated exactly this case: "null for a walk-in-only record never claimed by a self-service booking") — this feature simply exercises that existing, already-tested constructor path directly, with no change to 009's code at all.

**Alternatives considered**: Adding a new "create walk-in, no account" method to `PatientLinkingService` — rejected; it would mix two genuinely different concerns (find-or-link against a known account vs. create a fresh unlinked record) into one service, when the existing constructor already does exactly what this feature needs with zero new code in another module.

## Decision: One-Booking-per-Slot via a DB unique constraint on `booking.slot_id`, plus an application-level `status == OPEN` pre-check

**Rationale**: Two front-desk staff booking the same walk-in Slot at nearly the same moment is a realistic scenario (unlike 011's schedule-generation race), so this needs a real, data-layer-closed guarantee (Constitution IV), not just an app-level check-then-write. The `status == OPEN` check stays as the fast, common-case path (an already-booked Slot is rejected immediately, with a clear exception, without even attempting a write); the unique constraint is the actual backstop for the rare simultaneous-attempt case, translated to the same `SlotAlreadyBookedException` on a caught `DataIntegrityViolationException`.

**Alternatives considered**: A `SELECT ... FOR UPDATE` row lock on the Slot instead of a unique constraint — rejected as unnecessary additional locking machinery (Principle II) when a unique constraint already closes the race completely and more simply, with a pattern (catch-and-translate) already established elsewhere in this codebase (e.g. 009's own race-recovery precedent, adapted here to reject rather than recover, since — unlike 009's "same account, same intent" race — a booking race genuinely has one legitimate winner and one legitimate loser).

## Decision: `paymentStatus` starts `PENDING`, with no mutator exposed by this feature

**Rationale**: Spec Scope Decisions/Assumptions — the source material describes the *existence* of a manual PENDING/PAID flag but not *this* feature (the booking act itself) as where the flip happens. Building an unrequested toggle endpoint now would be exactly the kind of speculative surface this backlog has consistently avoided.

**Alternatives considered**: Adding a `PATCH .../payment-status` endpoint now — rejected as out of this feature's actual described scope; a future, small follow-on feature (or a later revision of this one, given a real requirement) is the right place for it.

## Decision: The new endpoint extends the existing `/api/v1/clinics/**` chain with one explicit matcher — no new chain

**Rationale**: `POST /api/v1/clinics/{clinicId}/slots/{slotId}/book` is genuinely clinic-scoped (the Slot belongs to a Session which belongs to a Clinic), so it fits the existing chain's own path prefix and authorization model exactly, unlike 015's `AppointmentType`/`DoctorDefaultFee` (doctor-scoped, needed a new chain). Explicitly adding this matcher — rather than assuming the existing chain's `anyRequest().authenticated()`-style coverage — closes exactly the gap class already found and fixed once this session (016-schedule-edit-non-retroactivity's PATCH path).

**Alternatives considered**: None seriously — a new chain would be pure duplication for a path prefix this feature already, correctly, falls under.
