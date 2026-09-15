-- 018-fixed-time-slot-pregeneration: one bookable time unit within a Fixed-Time Session.
-- Created for every applicable interval in the same step as its parent Session (011),
-- never lazily on booking - that distinction belongs to Queue/Token mode (013, not yet
-- built), which never gets rows in this table from this feature.

CREATE TABLE slot (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id  UUID NOT NULL REFERENCES session (id),
    start_time  TIME NOT NULL,
    end_time    TIME NOT NULL,
    is_buffer   BOOLEAN NOT NULL DEFAULT false,
    status      VARCHAR(20) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_slot_session_id ON slot (session_id);
