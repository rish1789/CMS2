# Contract: Reserved-Capacity Removal

This is a removal, not a new interface — this document records exactly what changes in each
existing endpoint's observable contract, since three endpoints' behavior shifts.

## `POST /api/v1/clinics/{clinicId}/slots/{slotId}/book` (staff booking, existing endpoint)

**Changed**: the `409 Conflict` this endpoint could return with `SLOT_RESERVED_FOR_WALK_IN`
can no longer occur — that error class and its throw site are removed. Every other response
(`201 Created`, `404 Not Found`, `409` for `SlotAlreadyBookedException`) is unchanged.

## `POST /api/v1/patients/slots/{slotId}/book` (patient self-service booking, existing endpoint)

**Changed**: same removal as above — the `SLOT_RESERVED_FOR_WALK_IN` `409` can no longer
occur. Every other response is unchanged.

## `GET .../slots` open-slot listing endpoints (patient self-service browsing, existing)

**Changed**: previously excluded any `isBuffer = true` slot from what a patient could browse
(`SlotRepository.findOpenFixedTimeSlots`/`findOpenFixedTimeSlotsOnDate`'s `AND s.isBuffer = false`
predicate). That predicate is removed — every `OPEN` slot is now listed, since no slot is ever
anything but directly bookable. Response shape is unchanged; only which rows are included changes
(strictly more rows than before, for a session that would previously have held back capacity).

## `GET .../sessions/{sessionId}/day-sheet` (existing endpoint)

**Changed**: each slot entry in the response drops its `isBuffer` boolean field. Every other field
is unchanged. This is a strict subset of the previous response shape — no field is renamed or
retyped, one is simply no longer present.

## `POST .../sessions/{sessionId}/walk-in` (walk-in insertion, existing endpoint)

**Changed**: the internal tier search this endpoint uses to pick a slot narrows from 3 tiers to 2
(data-model.md). Observable effect: a walk-in can no longer land on a slot with zero override
reason via the former buffer tier; if no no-show-freed slot exists, every walk-in now requires an
override reason (matching what was already true for the *old* tier 3). Success/error response
shapes (`200`, `403`, `404`, `409` for `NoSlotAvailableException`/`OverrideReasonRequiredException`)
are otherwise unchanged.
