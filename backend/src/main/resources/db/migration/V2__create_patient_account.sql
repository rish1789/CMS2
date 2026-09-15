-- 002-patient-account-login: the patient's global, self-service login identity.
-- Deliberately independent of `account`/`clinic`/`role_assignment` (001) - no foreign
-- key, no shared uniqueness constraint (FR-004, FR-008). pgcrypto already enabled by V1.

CREATE TABLE patient_account (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email                VARCHAR(255) NOT NULL,
    password_hash        VARCHAR(255) NOT NULL,
    mobile               VARCHAR(20),
    notification_opt_in  BOOLEAN NOT NULL DEFAULT TRUE,
    active               BOOLEAN NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_patient_account_email UNIQUE (email)
);
