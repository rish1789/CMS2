-- 007-doctor-profile-license-queue: adds the public-visibility toggle (defaults
-- visible so licenseVerified=false remains the sole discovery gate until Super Admin
-- acts) and a uniqueness constraint on license_number - the DB-layer backstop for the
-- onboarding-time dedup race (Constitution Principle IV), mirroring how
-- uq_account_email already backstops 004's app-level email check.

ALTER TABLE doctor_profile
    ADD COLUMN visible BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE doctor_profile
    ADD CONSTRAINT uq_doctor_profile_license_number UNIQUE (license_number);
