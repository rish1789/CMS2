-- 068-per-clinic-fees US3 (FR-008/FR-009, SC-003; owner decision 2026-10-01 "copy to every
-- clinic"): today's doctor-wide prices become each clinic's own starting prices, at every clinic
-- where the doctor holds an active Doctor role. Insert-only - the doctor-wide rows are kept as
-- the pre-068 audit trail and are no longer read (FR-012). updated_by_account_id stays null to
-- mark a copied row. ON CONFLICT keeps the copy safe to re-run.

INSERT INTO clinic_doctor_fee (clinic_id, doctor_profile_id, amount)
SELECT DISTINCT ra.clinic_id, ddf.doctor_profile_id, ddf.amount
FROM doctor_default_fee ddf
JOIN doctor_profile dp ON dp.id = ddf.doctor_profile_id
JOIN role_assignment ra ON ra.account_id = dp.account_id AND ra.role = 'Doctor' AND ra.active
ON CONFLICT (clinic_id, doctor_profile_id) DO NOTHING;

INSERT INTO clinic_appointment_type_price (clinic_id, appointment_type_id, amount)
SELECT DISTINCT ra.clinic_id, at.id, at.fee_override
FROM appointment_type at
JOIN doctor_profile dp ON dp.id = at.doctor_profile_id
JOIN role_assignment ra ON ra.account_id = dp.account_id AND ra.role = 'Doctor' AND ra.active
WHERE at.fee_override IS NOT NULL
ON CONFLICT (clinic_id, appointment_type_id) DO NOTHING;
