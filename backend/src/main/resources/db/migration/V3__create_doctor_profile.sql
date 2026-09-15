-- 004-staff-onboarding-direct-hire: the Doctor Profile row created alongside a Doctor
-- hire's Account/RoleAssignment. One row per Account (global Doctor identity, not
-- per-clinic). license_verified starts false; 005-doctor-profile-auto-creation-license-
-- queue owns the verification workflow that flips it, not this migration.

CREATE TABLE doctor_profile (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id         UUID NOT NULL REFERENCES account (id),
    specialization     VARCHAR(255) NOT NULL,
    license_number     VARCHAR(100) NOT NULL,
    experience_years   INTEGER NOT NULL,
    license_verified   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_doctor_profile_account UNIQUE (account_id)
);
