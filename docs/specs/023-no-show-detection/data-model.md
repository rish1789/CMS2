# Data Model: Automatic No-Show Detection

## New Migration

`V13__slot_no_show_and_hold_support.sql`:

```sql
ALTER TABLE slot ADD COLUMN on_hold BOOLEAN NOT NULL DEFAULT false;
```

`SlotStatus` (a Java enum persisted `@Enumerated(EnumType.STRING)`) needs no migration of its
own — adding `NO_SHOW` as a new enum constant requires no schema change, exactly as adding
`BOOKED` required none in 020.

## Extended Entities

| Entity | Change |
|---|---|
| `SlotStatus` (`com.cms.scheduling`) | Gains `NO_SHOW` — `OPEN, BOOKED, NO_SHOW`. |
| `Slot` (`com.cms.scheduling`) | Gains `onHold` (boolean, default `false`, plain getter + setter — no HTTP endpoint or caller yet, per Clarifications). |

## New Repository Query

`SlotRepository` gains one read query (no migration, no new table):

```java
@Query("SELECT s FROM Slot s WHERE s.status = com.cms.scheduling.SlotStatus.BOOKED "
     + "AND s.onHold = false "
     + "AND s.session.mode = com.cms.scheduling.ScheduleMode.FIXED_TIME")
List<Slot> findBookedFixedTimeCandidatesForNoShow();
```

Returns candidates for the sweep to filter further in Java (combining `Session.sessionDate` +
`Slot.startTime` into a comparable instant against "now minus 10 minutes" — see research.md
for why this isn't done as a single SQL predicate).

## New Service

`NoShowDetectionService.detectAndMarkNoShows()`: queries candidates, filters by elapsed grace
period, and for each one still eligible, sets `status = NO_SHOW` and saves — no separate
`@Transactional` wrapper (research.md). Returns the count marked, for logging (mirrors
`SessionGenerationService.generate`'s own `int` return-and-log pattern).

## New Scheduled Trigger

`NoShowDetectionTrigger` (`@Component`, `@Scheduled(cron = "0 * * * * *")`): calls
`NoShowDetectionService.detectAndMarkNoShows()` once per minute and logs the count, mirroring
`NightlySessionGenerationTrigger`'s exact shape.

## State Transitions

`Slot.status`: `BOOKED` → `NO_SHOW`, one-way, at most once per Slot (a Slot already `NO_SHOW`
no longer matches the candidate query's `status = BOOKED` filter, so it is never reprocessed —
SC-004). `Slot.onHold`, when `true`, structurally excludes the Slot from the candidate query
entirely (FR-002) — not a race-prone secondary check, a filter predicate in the query itself.
