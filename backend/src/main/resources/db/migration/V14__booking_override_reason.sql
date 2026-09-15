-- 025-walk-in-priority-insertion: retains the required written justification for a
-- priority-(3) walk-in insertion (FR-004). Null for every priority-(1)/(2) insertion and
-- for every pre-existing Booking row.
ALTER TABLE booking ADD COLUMN override_reason TEXT;
