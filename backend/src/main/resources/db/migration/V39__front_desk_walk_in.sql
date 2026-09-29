-- 063-front-desk-walk-in (data-model.md): the front-desk walk-in line and visit timing.
-- Every column is nullable: existing rows predate all of this, and booked (non-walk-in) visits
-- don't collect a visit reason. The "required for walk-ins" rule lives in FrontDeskWalkInService;
-- the database guarantees the one invariant that must never be violated from any path.

-- Why the patient came in: one of the VisitReason enum names, with free text for OTHER.
ALTER TABLE booking ADD COLUMN visit_reason VARCHAR(40);
ALTER TABLE booking ADD COLUMN visit_reason_detail VARCHAR(200);
ALTER TABLE booking ADD CONSTRAINT ck_booking_visit_reason_other_detail
    CHECK (visit_reason IS DISTINCT FROM 'OTHER'
           OR (visit_reason_detail IS NOT NULL AND length(trim(visit_reason_detail)) > 0));

-- Optional contact email on the clinic patient record (not a login; cleared on anonymization).
ALTER TABLE patient ADD COLUMN email VARCHAR(254);

-- When the patient was sent in to the doctor (Appeared) and when the visit finished (Completed).
ALTER TABLE slot ADD COLUMN appeared_at TIMESTAMPTZ;
ALTER TABLE slot ADD COLUMN completed_at TIMESTAMPTZ;
