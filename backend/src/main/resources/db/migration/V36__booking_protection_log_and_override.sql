-- 060-booking-abuse-prevention: the two booking-module-owned tables for the booking-limit and
-- rate-limit checks (research.md Decision 3 - these checks live in `booking`, not the new
-- `protection` module, to avoid a module dependency cycle). booking_attempt_log is append-only
-- (one row per attempt, whatever the outcome - FR-008); clinic_booking_limit_override is the
-- optional, at-most-one-per-clinic supplementary cap (FR-004); its change log is append-only
-- audit history (AUD-003, research.md Decision 9).

CREATE TABLE booking_attempt_log (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_account_id UUID NOT NULL REFERENCES patient_account (id),
    clinic_id          UUID NOT NULL REFERENCES clinic (id),
    attempted_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    outcome            VARCHAR(20) NOT NULL,
    booking_id         UUID REFERENCES booking (id)
);

CREATE INDEX ix_booking_attempt_log_patient_attempted_at
    ON booking_attempt_log (patient_account_id, attempted_at);

CREATE TABLE clinic_booking_limit_override (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id                UUID NOT NULL REFERENCES clinic (id),
    max_active_appointments  INTEGER NOT NULL,
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by               VARCHAR(255) NOT NULL,
    CONSTRAINT uq_clinic_booking_limit_override_clinic UNIQUE (clinic_id)
);

CREATE TABLE clinic_booking_limit_override_change_log (
    id                                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id                         UUID NOT NULL REFERENCES clinic (id),
    previous_max_active_appointments  INTEGER,
    new_max_active_appointments       INTEGER,
    changed_at                        TIMESTAMPTZ NOT NULL DEFAULT now(),
    changed_by                        VARCHAR(255) NOT NULL
);

CREATE INDEX ix_clinic_booking_limit_override_change_log_clinic
    ON clinic_booking_limit_override_change_log (clinic_id, changed_at DESC);
