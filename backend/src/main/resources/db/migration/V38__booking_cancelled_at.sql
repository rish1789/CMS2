-- 060-booking-abuse-prevention: booking previously had no cancellation timestamp - only its
-- current status. The repeated-cancellations admin-flagging signal (FR-017) needs "cancelled
-- within a rolling window," which is otherwise unanswerable. Nullable: every pre-existing
-- cancelled row simply has no known cancellation time and is excluded from the signal's window
-- (a one-time historical gap, not an ongoing one - every cancellation from here on sets it).

ALTER TABLE booking ADD COLUMN cancelled_at TIMESTAMPTZ;
