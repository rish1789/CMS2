CREATE TABLE external_record_reference (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id        UUID NOT NULL REFERENCES booking (id),
    doctor_profile_id UUID NOT NULL REFERENCES doctor_profile (id),
    record_type       VARCHAR(255) NOT NULL,
    source_provider   VARCHAR(255) NOT NULL,
    record_date       DATE NOT NULL,
    summary           TEXT NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL
);
