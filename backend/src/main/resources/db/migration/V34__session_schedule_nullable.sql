-- 055-schedule-break-window: ScheduleDeletionService detaches (schedule_id -> NULL) a Session
-- that still has real Booking/waitlist-offer history instead of blocking the whole Schedule
-- deletion outright - the Session already snapshots every field it needs at generation time
-- (start/end/mode/slotIntervalMinutes/breakStart/breakEnd), so it stays fully valid, complete
-- audit-trail data with no parent Schedule row. Sessions with zero activity are still deleted
-- outright by the same operation, same as a direct single-Session delete.
ALTER TABLE session ALTER COLUMN schedule_id DROP NOT NULL;
