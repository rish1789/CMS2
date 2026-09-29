-- 064-queue-send-in-complete (FR-011, data-model.md): from 064 on, a queue token is minted as
-- waiting (BOOKED) when it is booked, so it can be sent in and completed and counted in queue
-- position. Carry over the active queue bookings made before 064, whose tokens are still in the
-- old OPEN state. Cancelled and completed bookings are left exactly as they are. Idempotent.

UPDATE slot SET status = 'BOOKED'
WHERE status = 'OPEN'
  AND start_time IS NULL
  AND session_id IN (SELECT id FROM session WHERE mode = 'QUEUE')
  AND id IN (SELECT slot_id FROM booking WHERE status = 'ACTIVE');
