# Data Model: Unified Real-Time Inbox

## InboxItem

New entity, new `com.cms.inbox` module. One row per outstanding-or-resolved work item.

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | PK, generated |
| `clinic` | `@ManyToOne Clinic`, not null | Scoping FK (FR-001, FR-006) |
| `itemType` | `enum InboxItemType` (`WALK_IN`, `WAITLIST_OFFER`, `DEVERIFICATION_CASCADE`), not null | Extensible in place for future types, mirroring `SlotStatus`/`NotificationEventStatus`'s "define only what's needed now" precedent |
| `booking` | `@ManyToOne Booking`, nullable, `ON DELETE CASCADE` | Set only for `WALK_IN` (research.md R4/R5) |
| `waitlistEntry` | `@ManyToOne WaitlistEntry`, nullable, `ON DELETE CASCADE` | Set only for `WAITLIST_OFFER` |
| `doctorName` | `String`, nullable | Set for `DEVERIFICATION_CASCADE` when the cascade was doctor-scoped (008 Trigger 2); left null for a clinic-wide de-verification cascade (008 Trigger 1), which can span every doctor at the clinic — frozen either way, since Doctor identity is never anonymized (research.md R4) |
| `cancelledBookingCount` | `Integer`, nullable | Set only for `DEVERIFICATION_CASCADE` |
| `status` | `enum InboxItemStatus` (`UNCLAIMED`, `CLAIMED`, `RESOLVED`), not null, default `UNCLAIMED` | |
| `claimedByAccountId` | `UUID`, nullable | Plain id, not a JPA relation — mirrors `Booking`'s own `bookedByAccountId`-shaped fields for cross-module actor references |
| `createdAt` | `Instant`, not null, default now | Used for the oldest-first default ordering (spec Assumptions) |

**Validation / invariants**:
- Exactly one of `booking` / `waitlistEntry` / (`doctorName` + `cancelledBookingCount`) is populated,
  matching `itemType` — enforced in `InboxItemService`'s three `create*` factory methods, not by a
  DB constraint (mirrors this codebase's existing preference for type-conditional nullable columns
  over inheritance, e.g. `Slot`'s nullable `startTime`/`tokenNumber`).
- `claimedByAccountId` is null iff `status == UNCLAIMED`.
- `status` only ever moves `UNCLAIMED → CLAIMED → RESOLVED` or `CLAIMED → UNCLAIMED` (release) —
  never backward from `RESOLVED`.

## State Transitions

```text
UNCLAIMED --claim(accountId)--> CLAIMED
CLAIMED   --release(accountId, must match claimedByAccountId)--> UNCLAIMED
CLAIMED   --resolve(accountId, must match claimedByAccountId)--> RESOLVED
CLAIMED   --auto-resolve (WAITLIST_OFFER only, on offer claim/decline/expiry)--> RESOLVED
UNCLAIMED --auto-resolve (WAITLIST_OFFER only, on offer claim/decline/expiry)--> RESOLVED
```

Every transition is guarded by a conditional `@Modifying` update — `claimIfUnclaimed`,
`releaseIfClaimedBy`, `resolveIfClaimedBy` keyed on `id` (+ `accountId` where applicable) and
current `status`, plus `resolveIfNotResolved(waitlistEntryId)` for auto-resolve — closing every
race at the data layer (research.md R8), including the one between a staff-initiated `resolve` and
a concurrent waitlist-lifecycle auto-resolve landing on the same item (analyze finding F1).
Auto-resolve is unconditional on *claim* state only — an outstanding offer item resolves whether
or not any staff member claimed it (FR-013), since the underlying offer's own lifecycle, not staff
action, drives that transition — but is still guarded against double-resolving.

## Relationships

- `Clinic 1─N InboxItem` (per the source design's own stated relationship).
- `Booking 0─1 InboxItem` (a given Booking gets at most one `WALK_IN` item, created once at
  insertion time).
- `WaitlistEntry 0─1 InboxItem` (a given offer gets at most one `WAITLIST_OFFER` item, created
  once per offer — 032's "repeat until claimed or exhausted" design re-offers the *next*
  longest-waiting existing `WaitlistEntry`, not a newly created row, once the declined/expired
  entry's own item has already auto-resolved to `RESOLVED`; that next entry gets its own new
  Inbox Item when `matchAndOffer` runs again, analyze finding F2).

## Migration

New Flyway migration `V23__create_inbox_item.sql`:
- `inbox_item` table with the columns above.
- FK `booking_id → booking.id ON DELETE CASCADE` (nullable).
- FK `waitlist_entry_id → waitlist_entry.id ON DELETE CASCADE` (nullable).
- FK `clinic_id → clinic.id` (not nullable, no cascade — a Clinic is never deleted by this system).
- Index on `(clinic_id, status)` — the list endpoint's primary query shape (outstanding items for
  a clinic).
