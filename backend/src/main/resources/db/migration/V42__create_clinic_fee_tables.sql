-- 068-per-clinic-fees (SEC-03, owner decision B 2026-10-01): prices become clinic-scoped. A
-- doctor's default fee and an appointment type's price are stored per clinic, so one clinic can
-- never change what patients pay at another. Appointment types themselves stay doctor-level.
-- The doctor-wide doctor_default_fee / appointment_type.fee_override (V9) are left in place but
-- no longer read (FR-012); V43 copies them into these tables.

-- FR-001: at most one default fee per (clinic, doctor).
CREATE TABLE clinic_doctor_fee (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id              UUID NOT NULL REFERENCES clinic (id),
    doctor_profile_id      UUID NOT NULL REFERENCES doctor_profile (id),
    amount                 NUMERIC(10, 2) NOT NULL CHECK (amount >= 0),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by_account_id  UUID REFERENCES account (id),
    CONSTRAINT uq_clinic_doctor_fee UNIQUE (clinic_id, doctor_profile_id)
);

CREATE INDEX idx_clinic_doctor_fee_doctor ON clinic_doctor_fee (doctor_profile_id);

-- FR-002: at most one price per (clinic, appointment type).
CREATE TABLE clinic_appointment_type_price (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id              UUID NOT NULL REFERENCES clinic (id),
    appointment_type_id    UUID NOT NULL REFERENCES appointment_type (id),
    amount                 NUMERIC(10, 2) NOT NULL CHECK (amount >= 0),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by_account_id  UUID REFERENCES account (id),
    CONSTRAINT uq_clinic_appointment_type_price UNIQUE (clinic_id, appointment_type_id)
);

CREATE INDEX idx_clinic_appointment_type_price_type ON clinic_appointment_type_price (appointment_type_id);
