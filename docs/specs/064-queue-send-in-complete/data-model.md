# Data Model: Send-In and Complete for Queue Sessions (064)

**No schema change.** One data-only migration: `V40__queue_tokens_booked.sql`.

## Queue token lifecycle (existing `slot` rows, untimed, with `token_number`)

```
minted at booking --> BOOKED (waiting) --Send in (Appeared)--> APPEARED --Complete--> COMPLETED
                         |                  appeared_at stamped            completed_at stamped
                         +--staff cancel--> booking CANCELLED, slot OPEN (number never re-issued,
                                             never offered to the waitlist)
```

Before 064 a token was minted `OPEN`, and no transition beyond cancellation existed.

## Queue position

The number of `BOOKED` tokens in the same session with a lower `token_number`, plus 1. Shown only while the patient's own token is `BOOKED`.

## Migration V40 (FR-011)

```sql
UPDATE slot SET status = 'BOOKED'
WHERE status = 'OPEN' AND start_time IS NULL
  AND session_id IN (SELECT id FROM session WHERE mode = 'QUEUE')
  AND id IN (SELECT slot_id FROM booking WHERE status = 'ACTIVE');
```

Idempotent. It touches only active queue bookings still in the old state (2 rows in development today).
