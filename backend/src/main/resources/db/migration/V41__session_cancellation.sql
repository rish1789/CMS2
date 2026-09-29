-- 065-phase1-stabilization: session_cancellation (see specs/065-phase1-stabilization/data-model.md)

CREATE TABLE session_cancellation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES session (id),
    from_time TIME,
    to_time TIME,
    cancelled_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    cancelled_by_account_id UUID NOT NULL REFERENCES account (id),
    CONSTRAINT ck_session_cancellation_range
        CHECK (to_time IS NULL OR (from_time IS NOT NULL AND to_time > from_time))
);

CREATE UNIQUE INDEX uq_session_cancellation_whole
    ON session_cancellation (session_id)
    WHERE from_time IS NULL;

CREATE INDEX idx_session_cancellation_session
    ON session_cancellation (session_id);
