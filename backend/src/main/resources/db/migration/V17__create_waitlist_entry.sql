CREATE TABLE waitlist_entry (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id            UUID NOT NULL REFERENCES clinic (id),
    patient_account_id   UUID NOT NULL REFERENCES patient_account (id),
    doctor_profile_id    UUID REFERENCES doctor_profile (id),
    specialization       VARCHAR(255),
    status               VARCHAR(20) NOT NULL,
    joined_at            TIMESTAMPTZ NOT NULL,
    offered_at           TIMESTAMPTZ,
    offer_expires_at     TIMESTAMPTZ
);
