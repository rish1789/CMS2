-- 009-patient-record-phone-linking: the clinic-scoped Patient record, first defined
-- here (no prior feature needed one). Two SEPARATE partial-unique constraints, not one
-- flat clinic+phone constraint - resolved during Clarify to protect a Patient Account
-- from silently gaining access to another account's already-linked clinical history via
-- a shared phone number, while still closing the same-account double-submit race and
-- the duplicate-unlinked-walk-in race independently. See data-model.md.

CREATE TABLE patient (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clinic_id           UUID NOT NULL REFERENCES clinic (id),
    patient_account_id  UUID REFERENCES patient_account (id),
    name                VARCHAR(255) NOT NULL,
    phone               VARCHAR(20),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- FR-005(a): one account never has two Patient records at the same clinic.
CREATE UNIQUE INDEX uq_patient_clinic_account
    ON patient (clinic_id, patient_account_id)
    WHERE patient_account_id IS NOT NULL;

-- FR-005(b): two unlinked (walk-in) records never share a clinic+phone.
CREATE UNIQUE INDEX uq_patient_clinic_phone_unlinked
    ON patient (clinic_id, phone)
    WHERE patient_account_id IS NULL;
