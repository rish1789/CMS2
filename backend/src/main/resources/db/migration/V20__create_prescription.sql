CREATE TABLE prescription (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id        UUID NOT NULL REFERENCES booking (id),
    doctor_profile_id UUID NOT NULL REFERENCES doctor_profile (id),
    created_at        TIMESTAMPTZ NOT NULL
);

CREATE TABLE prescription_item (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prescription_id   UUID NOT NULL REFERENCES prescription (id),
    medication_name   VARCHAR(255) NOT NULL,
    dosage            VARCHAR(255) NOT NULL,
    frequency         VARCHAR(255) NOT NULL,
    duration          VARCHAR(255) NOT NULL,
    instructions      TEXT
);
