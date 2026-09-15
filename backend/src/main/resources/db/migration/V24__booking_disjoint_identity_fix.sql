-- _diagnostics CRITICAL - [BOOKING] - [FK_INTEGRITY_GAP]: booked_by_account_id was designed only
-- for the staff-booking case (references account, the staff identity system). The patient
-- self-service booking paths (017/018) write a patient_account.id into it instead - a disjoint
-- identity system with no overlapping UUID space (see V2's own comment). Fix: make
-- booked_by_account_id nullable and add a second, nullable booked_by_account_id counterpart
-- scoped to patient_account, mirroring Patient.patient_account_id's existing nullable-FK
-- precedent (V5) for the same disjoint-identity reason. Exactly one of the two is populated per
-- booking, enforced below.

ALTER TABLE booking
    ALTER COLUMN booked_by_account_id DROP NOT NULL;

ALTER TABLE booking
    ADD COLUMN booked_by_patient_account_id UUID REFERENCES patient_account (id);

ALTER TABLE booking
    ADD CONSTRAINT ck_booking_booked_by_exactly_one CHECK (
        (booked_by_account_id IS NOT NULL) <> (booked_by_patient_account_id IS NOT NULL)
    );
