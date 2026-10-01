package com.cms.booking.integration;

import com.cms.booking.domain.AppointmentType;
import com.cms.identity.doctor.DoctorProfile;
import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 068-per-clinic-fees test fixtures: seeds the clinic-scoped prices that bookings now resolve
 * from, at every clinic where the doctor holds an active Doctor role - the same rows V43 copies
 * from the retired doctor-wide fields. Call it after the doctor has been staffed at the clinic.
 */
@Component
public class ClinicPriceFixtures {

    private final JdbcTemplate jdbcTemplate;

    public ClinicPriceFixtures(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Prices the type at each clinic where its doctor is actively staffed. */
    public AppointmentType priceAtStaffedClinics(AppointmentType type, BigDecimal amount) {
        jdbcTemplate.update(
                """
                INSERT INTO clinic_appointment_type_price (clinic_id, appointment_type_id, amount)
                SELECT DISTINCT ra.clinic_id, at.id, ?
                FROM appointment_type at
                JOIN doctor_profile dp ON dp.id = at.doctor_profile_id
                JOIN role_assignment ra ON ra.account_id = dp.account_id AND ra.role = 'Doctor' AND ra.active
                WHERE at.id = ?
                ON CONFLICT (clinic_id, appointment_type_id) DO UPDATE SET amount = EXCLUDED.amount
                """,
                amount,
                type.getId());
        return type;
    }

    /** Sets the doctor's default fee at each clinic where they are actively staffed. */
    public void defaultFeeAtStaffedClinics(DoctorProfile doctor, BigDecimal amount) {
        jdbcTemplate.update(
                """
                INSERT INTO clinic_doctor_fee (clinic_id, doctor_profile_id, amount)
                SELECT DISTINCT ra.clinic_id, dp.id, ?
                FROM doctor_profile dp
                JOIN role_assignment ra ON ra.account_id = dp.account_id AND ra.role = 'Doctor' AND ra.active
                WHERE dp.id = ?
                ON CONFLICT (clinic_id, doctor_profile_id) DO UPDATE SET amount = EXCLUDED.amount
                """,
                amount,
                doctor.getId());
    }

    /** Price rows reference clinic, doctor and appointment type - delete them before those. */
    public void deleteAll() {
        jdbcTemplate.update("DELETE FROM clinic_appointment_type_price");
        jdbcTemplate.update("DELETE FROM clinic_doctor_fee");
    }
}
