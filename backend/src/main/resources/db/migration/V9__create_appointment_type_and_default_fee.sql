-- 017-fee-resolution-locking: doctor-scoped (not clinic-scoped) fee configuration - the
-- input a future booking-creation feature (016/017/018, not yet built) will resolve
-- against and snapshot onto its own Booking record. No Booking table exists yet; this
-- migration touches nothing but this feature's own two new tables.

CREATE TABLE appointment_type (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_profile_id   UUID NOT NULL REFERENCES doctor_profile (id),
    name                VARCHAR(255) NOT NULL,
    fee_override        NUMERIC(10, 2),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE doctor_default_fee (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_profile_id   UUID NOT NULL REFERENCES doctor_profile (id),
    amount              NUMERIC(10, 2) NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- At most one default fee per doctor (spec Assumptions).
CREATE UNIQUE INDEX uq_doctor_default_fee_doctor_profile ON doctor_default_fee (doctor_profile_id);
