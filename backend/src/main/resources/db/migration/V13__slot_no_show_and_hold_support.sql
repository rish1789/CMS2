-- 023-no-show-detection: adds the currently-unset hold flag a Slot needs before automatic
-- no-show marking can correctly exempt held Slots (Clarifications - infrastructure ahead of
-- the not-yet-built feature that will actually set it). NO_SHOW itself is a new SlotStatus
-- enum constant, persisted as a string - it needs no schema change of its own.
ALTER TABLE slot ADD COLUMN on_hold BOOLEAN NOT NULL DEFAULT false;
