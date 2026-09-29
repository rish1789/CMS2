# Data Model: Self-Service Waitlist Claim

## `WaitlistEntryStatus` (extend, 028)

Adds two values to the existing enum:

- `CLAIMED` — a successful claim converted this entry into a confirmed Booking. Terminal.
- `EXPIRED` — the offer lapsed with no claim, or the patient explicitly declined (research.md R5
  models both as the same terminal state). Terminal.

`WAITING` and `OFFERED` (028) are unchanged.

## `WaitlistEntry` (extend, 028)

New field:

| Field | Type | Notes |
|---|---|---|
| `offeredSlot` | `Slot` (`@ManyToOne`, nullable) | Set only when transitioning to `OFFERED` (by `WaitlistMatchingService`, research.md R7); which specific Slot this offer refers to. Never set for a `WAITING` entry. |

No other fields change. `status`/`offeredAt`/`offerExpiresAt` are unchanged in shape, just gain
two new reachable values and get set again (with a fresh timestamp/window) each time
`WaitlistMatchingService.matchAndOffer` re-offers the same entry-pool.

### Migration

`V18__waitlist_entry_offered_slot.sql`:

```sql
ALTER TABLE waitlist_entry ADD COLUMN offered_slot_id UUID REFERENCES slot (id);
```

(No backfill needed — every existing row is `WAITING`, so `offered_slot_id` is legitimately null
for all of them.)

## Repository additions (`WaitlistEntryRepository`, extend)

- `claimIfOffered(UUID id, Instant now)` — `@Modifying` conditional update: `status = 'OFFERED'`
  → `CLAIMED`, only `WHERE status = 'OFFERED' AND offer_expires_at > :now`. Returns rows updated
  (0 or 1).
- `expireIfOffered(UUID id)` — `@Modifying` conditional update: `status = 'OFFERED'` → `EXPIRED`,
  `WHERE status = 'OFFERED'` (no time check — used by both an explicit decline, which may happen
  before or after the nominal window, and the sweep, which only ever calls it on entries it has
  already confirmed are lapsed). Returns rows updated (0 or 1).
- `findByStatusAndOfferExpiresAtBefore(WaitlistEntryStatus status, Instant now)` — every currently
  lapsed `OFFERED` entry, the expiry sweep's candidate set.

## No change to `Booking`, `Slot`, or `Session`

A claim's resulting `Booking` is created exactly as any other patient booking is (research.md
R2) — no new fields, no special-cased status.
