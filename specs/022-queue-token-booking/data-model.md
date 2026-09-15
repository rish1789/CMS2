# Data Model: Queue/Token Booking

No new entities and no new Flyway migration. This feature is pure new behavior (two service
+ controller pairs, three new exception mappings) over entities already defined by prior
converged features.

## Reused Entities (unchanged)

| Entity | Defined by | Fields relevant to this feature |
|---|---|---|
| `Session` | 015 (`com.cms.scheduling`) | `clinic`, `doctorProfile`, `mode` (must be `QUEUE`) — the target of a queue booking (not a `Slot`, since none exists yet). |
| `Slot` | 012/019 (`com.cms.scheduling`) | Minted fresh per booking via `QueueSlotService.issueNextSlot(sessionId)`; `tokenNumber` set, `startTime`/`endTime` null, `status = OPEN` at creation, immediately used by this Booking. |
| `Patient` | 019 (`com.cms.patient.record`) | Resolved/created exactly as 016 (staff walk-in-or-existing) or 021 (patient `PatientLinkingService.findOrCreatePatient`) already do. |
| `AppointmentType` / `FeeResolutionService` | 015 (`com.cms.booking`) | Unchanged; resolves/locks the fee before any write, identical ordering to 016/017/021. |
| `Booking` | 016 (`com.cms.booking`) | Created exactly as 016/017/021 create it: `slot`, `patient`, `appointmentType`, `lockedFee`, `paymentStatus=PENDING`, `bookedByAccountId`. No delay-in-minutes field exists on `Booking` at all (spec FR-008 is satisfied structurally — there is nothing to omit). |

## New Exceptions Mapped (no new exception types — all already exist in `com.cms.scheduling`)

| Exception | Existing since | New HTTP mapping |
|---|---|---|
| `SessionNotFoundException` | 015 | `404 SESSION_NOT_FOUND` |
| `NotAQueueSessionException` | 019 | `409 NOT_A_QUEUE_SESSION` |
| `TokenIssuanceFailedException` | 019 | `503 TOKEN_ISSUANCE_FAILED` |

## New DTOs

- **`QueueBookSlotRequest`** (staff): `patientId` (nullable), `patientName` (nullable, required
  if `patientId` is absent), `patientPhone` (nullable), `appointmentTypeId` (required) — same
  shape as 016's `BookSlotRequest`, since the staff actor/authorization model is identical.
- **`PatientQueueBookSlotRequest`** (patient): `patientName` (required — used only if
  `PatientLinkingService.findOrCreatePatient` creates a new record), `appointmentTypeId`
  (required) — same shape as 021's `PatientBookSlotRequest`.
- **`QueueBookingResponse`** (both, new): `id`, `slotId`, `tokenNumber`, `patientId`,
  `appointmentTypeId`, `lockedFee`, `paymentStatus`, `createdAt` — `BookingResponse`'s exact
  fields plus `tokenNumber` (read off the Booking's `Slot`). A new type rather than reusing
  `BookingResponse` as-is, since the user story's whole point ("receive a token number
  reflecting my place in the queue") requires the token number to actually be in the
  response — `BookingResponse` alone would force a second round trip just to learn it.

## State Transitions

`Slot.status` starts and stays `OPEN` through this feature's own flow (it is set `OPEN` by
`QueueSlotService`'s constructor and this feature never flips it to `BOOKED` — that transition
is Fixed-Time-mode-specific, per `Slot`'s own javadoc distinguishing the two constructors).
Whether a queue Slot's status should ever change once its one-and-only Booking exists is out of
this feature's scope (queue-position tracking, 024, unbuilt, is what would consume/observe it).
