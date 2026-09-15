-- 013-recurring-schedule-definition: the recurring weekly pattern that a future nightly
-- Session-generation job (011, not yet built) will consume. No Session/Slot table exists
-- yet - this migration touches nothing but this feature's own new tables.

CREATE TABLE schedule (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_profile_id       UUID NOT NULL REFERENCES doctor_profile (id),
    clinic_id               UUID NOT NULL REFERENCES clinic (id),
    start_time              TIME NOT NULL,
    end_time                TIME NOT NULL,
    mode                    VARCHAR(20) NOT NULL,
    slot_interval_minutes   INTEGER,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- @ElementCollection(Set<DayOfWeek>) join table - one row per (schedule, day).
CREATE TABLE schedule_day (
    schedule_id  UUID NOT NULL REFERENCES schedule (id),
    day_of_week  VARCHAR(10) NOT NULL,
    PRIMARY KEY (schedule_id, day_of_week)
);
