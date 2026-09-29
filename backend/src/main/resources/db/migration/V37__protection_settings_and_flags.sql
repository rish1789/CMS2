-- 060-booking-abuse-prevention: the new `protection` module's own tables. protection_setting is
-- the first runtime-editable admin setting anywhere in this system (research.md Decision 5);
-- its change log is append-only audit history (AUD-002, research.md Decision 9).
-- suspicious_activity_flag's partial unique index is the dedup guarantee (FR-014, Clarifications) -
-- at most one OUTSTANDING flag per (patient, clinic, signal type), enforced at the data layer, not
-- only in application code.

CREATE TABLE protection_setting (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    value       VARCHAR(255) NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by  VARCHAR(255) NOT NULL,
    CONSTRAINT uq_protection_setting_name UNIQUE (name)
);

CREATE TABLE protection_setting_change_log (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    setting_name   VARCHAR(100) NOT NULL,
    previous_value VARCHAR(255),
    new_value      VARCHAR(255) NOT NULL,
    changed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    changed_by     VARCHAR(255) NOT NULL
);

CREATE INDEX ix_protection_setting_change_log_name
    ON protection_setting_change_log (setting_name, changed_at DESC);

CREATE TABLE suspicious_activity_flag (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_account_id  UUID NOT NULL REFERENCES patient_account (id),
    clinic_id           UUID REFERENCES clinic (id),
    signal_type         VARCHAR(40) NOT NULL,
    reason              VARCHAR(500) NOT NULL,
    detected_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    status              VARCHAR(20) NOT NULL,
    resolved_at         TIMESTAMPTZ,
    resolved_by         VARCHAR(255)
);

CREATE UNIQUE INDEX uq_suspicious_activity_flag_outstanding
    ON suspicious_activity_flag (patient_account_id, clinic_id, signal_type)
    WHERE status = 'OUTSTANDING';

CREATE INDEX ix_suspicious_activity_flag_clinic_status
    ON suspicious_activity_flag (clinic_id, status);
