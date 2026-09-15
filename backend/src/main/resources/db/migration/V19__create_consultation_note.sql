CREATE TABLE consultation_note (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id        UUID NOT NULL UNIQUE REFERENCES booking (id),
    doctor_profile_id UUID NOT NULL REFERENCES doctor_profile (id),
    content           TEXT NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL
);
