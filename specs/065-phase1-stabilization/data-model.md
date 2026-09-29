# Data Model: 065 Phase 1 Stabilization

## New table: `session_cancellation` (migration `V41__session_cancellation.sql`)

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | UUID PK | no | `DEFAULT gen_random_uuid()`; entity uses `@UuidGenerator` (matches existing tables) |
| `session_id` | UUID | no | FK → `session(id)`, default NO ACTION (**not** cascade, per v41-design-review §8-H item 7). `SessionDeletionService.hasRealActivity` counts cancellation records, so no application path deletes a session that has one; a cancellation can never be silently lost and regenerated as bookable. |
| `from_time` | TIME | yes | NULL = whole-session cancellation |
| `to_time` | TIME | yes | NULL = until the end of the session. Only meaningful with `from_time`. |
| `cancelled_at` | TIMESTAMPTZ | no | `DEFAULT now()` |
| `cancelled_by_account_id` | UUID | no | FK → `account(id)`: the staff account that cancelled (owner decision 7; mirrors `booking.booked_by_account_id`) |

**Constraints and indexes:**
- `ck_session_cancellation_range`: `to_time IS NULL OR (from_time IS NOT NULL AND to_time > from_time)`
- `uq_session_cancellation_whole`: UNIQUE `(session_id)` WHERE `from_time IS NULL`. At most one whole-session record; closes the concurrent double-cancel race at the data layer.
- `idx_session_cancellation_session`: `(session_id)`, used by the `NOT EXISTS` sub-queries in listings

**Lifecycle:**
- Rows are **insert-only**. There is no update or delete path, and a session that has a record is never deleted (see `session_id`).
- No backfill: sessions cancelled before V41 carry no record.

**Entity:** `com.cms.scheduling.domain.SessionCancellationRecord` (`@ManyToOne Session session`, `LocalTime fromTime`, `LocalTime toTime`, `Instant cancelledAt`, `UUID cancelledByAccountId`).
**Repository:** `com.cms.scheduling.repository.SessionCancellationRecordRepository`.

## Derived rule: is a session accepting a booking at time *t*?

Given the current server time `now` (from the injected clock), a `session`, and an optional timed `slot`:

1. `session.sessionDate < now.date` → **PAST_DATE**
2. `slot` timed and `LocalDateTime(sessionDate, slot.startTime) < now` → **ELAPSED** (FR-012; a slot starting exactly now is still bookable, owner decision 5)
3. A whole record exists → **CANCELLED**
4. The probe time `p` is:
   - `slot.startTime` for a timed slot
   - `now.time` for untimed requests (queue, walk-in) **only when** `sessionDate == now.date`; otherwise no range check applies

   Any range record with `from_time <= p` and (`to_time` NULL or `p < to_time`) → **CANCELLED**
5. Otherwise → **ACCEPTING**

The same boundaries as spec 030's cutoff filter: inclusive lower bound, exclusive upper bound.

## Unchanged

- `slot.status` values and transitions. Cancelled bookings' slots still go to `OPEN`; bookability comes from the rule above.
- `session` columns.
- All existing constraints.
