package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cms.booking.domain.AppointmentType;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 068-per-clinic-fees FR-001/FR-002 (data-model.md): a doctor's default fee and an appointment
 * type's price are stored per clinic, at most one each per (clinic, doctor) and (clinic, type) -
 * enforced at the data layer (Constitution IV), with non-negative amounts.
 */
class ClinicFeeSchemaTest extends AbstractBookingIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Runs before the base cleanup (subclass @AfterEach first): the price rows reference clinic/doctor/type. */
    @AfterEach
    void deletePriceRows() {
        jdbcTemplate.update("DELETE FROM clinic_appointment_type_price");
        jdbcTemplate.update("DELETE FROM clinic_doctor_fee");
    }

    @Test
    void oneDefaultFeePerClinicAndDoctor() {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        insertDefaultFee(clinic.getId(), doctor.getId(), "500.00");

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM clinic_doctor_fee", Integer.class))
                .isEqualTo(1);
        assertThatThrownBy(() -> insertDefaultFee(clinic.getId(), doctor.getId(), "600.00"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theSameDoctorCanHaveADifferentDefaultFeeAtAnotherClinic() {
        var clinicA = saveClinic();
        var clinicB = saveClinic();
        var doctor = saveDoctorStaffedAt(clinicA);
        linkDoctorToClinic(doctor, clinicB, true);

        insertDefaultFee(clinicA.getId(), doctor.getId(), "300.00");
        insertDefaultFee(clinicB.getId(), doctor.getId(), "500.00");

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM clinic_doctor_fee", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void onePricePerClinicAndAppointmentType() {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        AppointmentType type = appointmentTypeRepository.save(new AppointmentType(doctor, "Procedure", null));
        insertTypePrice(clinic.getId(), type.getId(), "800.00");

        assertThatThrownBy(() -> insertTypePrice(clinic.getId(), type.getId(), "900.00"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void negativeAmountsAreRejected() {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        AppointmentType type = appointmentTypeRepository.save(new AppointmentType(doctor, "Procedure", null));

        assertThatThrownBy(() -> insertDefaultFee(clinic.getId(), doctor.getId(), "-1.00"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertTypePrice(clinic.getId(), type.getId(), "-0.01"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertDefaultFee(java.util.UUID clinicId, java.util.UUID doctorProfileId, String amount) {
        jdbcTemplate.update(
                "INSERT INTO clinic_doctor_fee (clinic_id, doctor_profile_id, amount) VALUES (?, ?, ?)",
                clinicId,
                doctorProfileId,
                new BigDecimal(amount));
    }

    private void insertTypePrice(java.util.UUID clinicId, java.util.UUID appointmentTypeId, String amount) {
        jdbcTemplate.update(
                "INSERT INTO clinic_appointment_type_price (clinic_id, appointment_type_id, amount) VALUES (?, ?, ?)",
                clinicId,
                appointmentTypeId,
                new BigDecimal(amount));
    }
}
