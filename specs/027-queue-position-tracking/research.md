# Research: Queue Position Tracking (Queue-Mode Only)

## R1: Module placement — `com.cms.booking`, keyed by Booking, not `com.cms.scheduling`

**Decision**: `QueuePositionService` lives in `com.cms.booking` — the query is keyed by `bookingId`
(a `com.cms.booking`-owned entity), even though it reads `Slot.tokenNumber`/`Slot.status`
(`com.cms.scheduling`-owned) to compute the answer.

**Rationale**: This is the mirror-image placement decision from 026's `SessionDelayService`
(`com.cms.scheduling`, keyed by `sessionId`) — there, the natural query key was a
`com.cms.scheduling`-owned entity; here, it's a `com.cms.booking`-owned one. Both decisions follow
the same rule (research.md R1 of 026): place the service where its *query key* entity lives, not
where the data it *reads* lives. `com.cms.booking` already depends on `com.cms.scheduling` (read
`Slot`/`Session` freely, as `WalkInInsertionService`/`FeeResolutionService` already do) — this
stays consistent with that established one-directional dependency.

**Alternatives considered**: `com.cms.scheduling`, keyed by `slotId` instead of `bookingId`.
Rejected — the spec's own language and both consumer audiences (staff, patient) think in terms of
"my/this booking's position," not "this slot's position"; forcing callers to first resolve a
Slot ID from a Booking ID they already have would be an unnecessary indirection.

## R2: Computed live at query time, no stored/cached figure

**Decision**: `QueuePositionService.position(bookingId)` recomputes from scratch on every call —
no new column, no trigger points, no write path at all.

**Rationale**: A direct reading of the business rule's own "recalculates" wording (Assumptions) —
unlike 026's delay figure, nothing in this feature's spec requires staleness-until-a-trigger; live
computation is strictly simpler (Constitution II) and there's no correctness reason to introduce
023's stored-and-triggered pattern here. The data it reads (`Slot.tokenNumber`/`status` for one
Session) is already proven cheap to load and filter in Java at this scale (021/025/026's own
precedent).

**Alternatives considered**: Mirroring 023's stored-`delayMinutes`-plus-recalculation-triggers
design exactly, for consistency between the two "day-of-operations status" features. Rejected —
that design exists specifically to satisfy 023's own "not a live timer" requirement, which this
feature's spec explicitly does not share; copying it here would add write paths, trigger call
sites, and staleness reasoning this feature's actual requirements don't need.

## R3: "Active" ahead-booking definition — excludes `COMPLETED`/`NO_SHOW`, not (yet) `CANCELLED`

**Decision**: An ahead Slot (lower `tokenNumber`, same Session) counts toward the position if its
`status == BOOKED`; `COMPLETED` and `NO_SHOW` are excluded. No `CANCELLED` status exists anywhere
in this codebase yet (build-order.md schedules cancellation features after this one), so it isn't
filterable — Assumptions/Edge Cases documents this explicitly as a known, deliberate limitation.

**Rationale**: Matches the spec's own worked example (tokens 1-2 completed excluded, token 3
active/BOOKED included) and this session's established precedent of building only what currently-
real states support (021's `onHold` flag shipped ahead of any feature setting it; 025's no-show
handling only reads states that already exist).

**Alternatives considered**: Adding a placeholder `CANCELLED` `SlotStatus` value now, unused, so a
later cancellation feature only needs to start setting it. Rejected — Constitution II: no current
requirement needs it; the later cancellation feature (025/026/027 in build-order.md, whichever
ships first) is the right place to both add the status *and* extend this feature's exclusion set
in the same change, since it will already be touching `SlotStatus` and can verify both together.

## R4: Two endpoints, one shared computation

**Decision**: `QueuePositionService.position(bookingId)` is called from two controllers:
- `GET /api/v1/clinics/{clinicId}/bookings/{bookingId}/queue-position` (staff — any active role at
  the clinic may call this, no Operations/ClinicAdmin-only gate, mirroring 026's "viewing is
  unrestricted by role" precedent; but a caller with *zero* role assignment at `clinicId` is still
  rejected `403 FORBIDDEN` via `RoleAssignmentRepository`'s new role-agnostic query (R5) — analyze
  finding E1, mirrors every other staff-gated action's identical pattern) — then filters the
  Booking by clinic via its Slot's Session's Clinic before calling the shared computation.
- `GET /api/v1/patients/bookings/{bookingId}/queue-position` (patient, own booking only — no
  `clinicId` in the path at all, since ownership is the actual security boundary here, not clinic
  membership; the Booking ID is already a globally-unique identifier).

Both call the identical `QueuePositionService.position(bookingId)` — clinic-scoping (staff) and
ownership-scoping (patient) are each caller-side checks *before* that shared call, not two
different computations.

**Rationale**: The position computation itself (R1-R3) doesn't depend on which audience is asking;
duplicating it per-audience would risk the two paths drifting apart (SC-005 explicitly requires
they never do). Two thin controllers over one shared service is the direct expression of that
requirement structurally, not just by convention.

**Alternatives considered**: A single unified endpoint serving both audiences via a shared JWT
scheme. Rejected — this codebase has never had a shared staff/patient auth scheme (deliberately
separate `StaffJwtService`/patient `JwtService`, separate `SecurityConfig` chains throughout); this
feature doesn't need to be the first to introduce one, and two thin, separately-secured controllers
over one shared service already achieves the actual requirement (identical computation) without it.

## R5: New role-agnostic "active staff at this clinic" repository query

**Decision**: Add `RoleAssignmentRepository.existsByAccount_IdAndClinic_IdAndActiveTrue(accountId,
clinicId)` — no `Role` parameter, unlike every existing query on this repository.

**Rationale**: FR-004/Assumptions explicitly say staff viewing is open to "any active staff member
at the clinic, doctor included" — every existing `RoleAssignmentRepository` query requires a
specific `Role`, because every prior staff-gated action in this codebase (016/020/025/026's
completion action) deliberately excludes at least one role (the Doctor). This is the first
staff-gated read with no role exclusion at all, so it needs a genuinely new query shape, not reuse
of an existing role-specific one.

**Alternatives considered**: Checking three roles individually (Operations, ClinicAdmin, Doctor) via
three existing-shaped calls OR'd together. Rejected — verbose and wrong by construction if a fourth
staff role is ever added (Constitution II favors the query that directly expresses "any active
role," not an enumeration that silently goes stale).
