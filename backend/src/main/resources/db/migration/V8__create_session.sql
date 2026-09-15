-- 015-nightly-session-generation: one concrete, bookable occurrence materialized from a
-- Recurring Schedule (009/V7) on a specific calendar date. Snapshots mode/time-range/
-- slot-interval at generation time (denormalized, not re-derived from schedule at read
-- time) so a later Schedule edit (014-schedule-edit-non-retroactivity, not yet built)
-- can never retroactively change an already-generated Session.

CREATE TABLE session (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    schedule_id             UUID NOT NULL REFERENCES schedule (id),
    clinic_id               UUID NOT NULL REFERENCES clinic (id),
    doctor_profile_id       UUID NOT NULL REFERENCES doctor_profile (id),
    session_date            DATE NOT NULL,
    mode                    VARCHAR(20) NOT NULL,
    start_time              TIME NOT NULL,
    end_time                TIME NOT NULL,
    slot_interval_minutes   INTEGER,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- FR-002/SC-002: the data-layer guarantee against duplicate generation for the same
-- (schedule, date) pair, regardless of how many times or from how many triggers
-- generation is invoked (research.md).
CREATE UNIQUE INDEX uq_session_schedule_date ON session (schedule_id, session_date);
