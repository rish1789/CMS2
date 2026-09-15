-- Super Admin console redesign: lets a Pending clinic/doctor registration that was never
-- genuine be rejected (with a required, structured reason) instead of left sitting in the
-- Pending queue forever or force-verified just to get it out of the way. Reversible by
-- design (see rejected/restore) - distinct from Un-verify/Revoke, which only ever apply to
-- an already-verified entity and mean something different (008-deverification-cascade).
ALTER TABLE clinic
    ADD COLUMN rejected BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN rejection_reason VARCHAR(30)
        CHECK (rejection_reason IN
            ('DUPLICATE_REGISTRATION', 'SUSPECTED_FRAUD', 'INVALID_DETAILS', 'UNREACHABLE_CONTACT', 'OTHER')),
    ADD COLUMN rejection_detail TEXT,
    ADD COLUMN rejected_at TIMESTAMP,
    ADD COLUMN rejected_by VARCHAR(255);

ALTER TABLE doctor_profile
    ADD COLUMN rejected BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN rejection_reason VARCHAR(30)
        CHECK (rejection_reason IN
            ('DUPLICATE_REGISTRATION', 'SUSPECTED_FRAUD', 'INVALID_DETAILS', 'UNREACHABLE_CONTACT', 'OTHER')),
    ADD COLUMN rejection_detail TEXT,
    ADD COLUMN rejected_at TIMESTAMP,
    ADD COLUMN rejected_by VARCHAR(255);
