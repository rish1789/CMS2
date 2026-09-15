-- super-admin-console-redesign: the Clinic/Doctor verification queues now support sort,
-- free-text search, and a Rejected tab with its own retention-purge sweep, all against tables
-- that can grow into the hundreds/thousands at scale (stress-test expectation). Postgres does
-- not auto-index foreign key or plain columns, so nothing here currently covers the tab-status
-- lookup, the retention-purge cutoff scan, or the sortable date columns.
CREATE INDEX idx_clinic_verified_rejected ON clinic (verified, rejected);
CREATE INDEX idx_clinic_rejected_rejected_at ON clinic (rejected, rejected_at);
CREATE INDEX idx_clinic_created_at ON clinic (created_at);
CREATE INDEX idx_clinic_name_lower ON clinic (lower(name));

CREATE INDEX idx_doctor_profile_license_verified_rejected ON doctor_profile (license_verified, rejected);
CREATE INDEX idx_doctor_profile_rejected_rejected_at ON doctor_profile (rejected, rejected_at);
CREATE INDEX idx_doctor_profile_created_at ON doctor_profile (created_at);

CREATE INDEX idx_account_name_lower ON account (lower(name));
