-- 020-staff-assisted-fixed-time-booking: one confirmed appointment. Unique slot_id is
-- the data-layer guarantee that a Slot is ever booked at most once, closing the
-- concurrent-double-booking race (Constitution IV) - not merely the application-level
-- status == OPEN check that sits in front of it.

CREATE TABLE booking (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slot_id                UUID NOT NULL REFERENCES slot (id),
    patient_id             UUID NOT NULL REFERENCES patient (id),
    appointment_type_id    UUID NOT NULL REFERENCES appointment_type (id),
    locked_fee             NUMERIC(10, 2) NOT NULL,
    payment_status         VARCHAR(20) NOT NULL,
    booked_by_account_id   UUID NOT NULL REFERENCES account (id),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_booking_slot ON booking (slot_id);
