-- 019-queue-slot-on-demand-generation: extends 012's slot table in place to support a
-- second shape - a Queue-mode Slot has a never-reused token_number and no fixed
-- start/end time (the opposite of a Fixed-Time Slot, which has a time window and no
-- token). Mirrors this backlog's established pattern of extending an entity in place
-- rather than introducing a parallel table for a new, later-arriving mode.

ALTER TABLE slot
    ALTER COLUMN start_time DROP NOT NULL,
    ALTER COLUMN end_time DROP NOT NULL,
    ADD COLUMN token_number INTEGER;

-- FR-003/SC-002: a token number, once issued for a session, is never reused - closed at
-- the data layer. Partial (not a plain unique index) since Fixed-Time slots all carry
-- token_number = NULL, which Postgres already treats as distinct from other NULLs, but
-- an explicit partial index keeps the intent self-documenting.
CREATE UNIQUE INDEX uq_slot_session_token
    ON slot (session_id, token_number)
    WHERE token_number IS NOT NULL;
