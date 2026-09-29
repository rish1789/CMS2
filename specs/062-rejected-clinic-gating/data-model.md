# Data Model: Rejected Clinics Stop Operating (062)

**No schema change. No Flyway migration.** Every rule reads fields that already exist.

## Existing entities read or written

| Entity | Field(s) | Use in this feature |
|---|---|---|
| Clinic | `rejected` | The single source of truth for "rejected". Read by the booking check, the session-generation skip, the staff access gate, and the clinic picker. Restore sets it back to `false`, and every rule lifts at once (FR-005). |
| Booking | `status`, `cancellationReason`, `cancelledAt` | Future bookings at a rejected clinic → `CANCELLED`, reason `CLINIC_REJECTED` (via the existing guarded `cancelIfActive`). |
| Slot | `status` | A slot freed by a rejection cancel goes back to `OPEN`. It can't be rebooked while the clinic is rejected (booking check). |
| WaitlistEntry | `status` | `WAITING`/`OFFERED` at a rejected clinic → `EXPIRED` (existing terminal status). |
| RoleAssignment | `role`, `active`, `clinic` | Doctor/Operations at a rejected clinic grant no access. `ClinicAdmin` still does. |
| Session / Schedule | — | No new sessions generated while rejected; existing rows are left in place. |

## Enum change

`BookingCancellationReason` gains `CLINIC_REJECTED`.
- **System-only**: it is set only by the rejection cascade. The patient cancel endpoint MUST refuse it as input (400 `INVALID_CANCELLATION_REASON`).
- **Storage**: `booking.cancellation_reason` is `VARCHAR(50)` with no check constraint (V31), so the new literal stores without a migration.

## New event (in-process, not persisted)

`ClinicRejectedEvent(UUID clinicId, Instant occurredAt)` lives in `identity.admin.domain`, next to `ClinicDeVerifiedEvent`. It is published only on a genuine not-rejected → rejected transition. Consumers run AFTER_COMMIT:
- **booking**: the rejection cancel cascade.
- **waitlist**: expire the clinic's open entries.

## State transitions

```
Clinic:  Pending --Reject--> Rejected --Restore--> Pending
            |                   |
            |                   +-- on entry: upcoming ACTIVE bookings -> CANCELLED(CLINIC_REJECTED), slot -> OPEN
            |                   +-- on entry: WAITING/OFFERED waitlist entries -> EXPIRED
            |                   +-- while in state: booking refused, no session generation,
            |                       non-ClinicAdmin staff access refused
            +-- (Verified clinics cannot be rejected; the existing rule is unchanged)

Restore does NOT reinstate cancelled bookings or expired waitlist entries.
```

"Upcoming" means: Booking `ACTIVE`, Slot `BOOKED`, Session date on or after today (research.md Decision 4).
