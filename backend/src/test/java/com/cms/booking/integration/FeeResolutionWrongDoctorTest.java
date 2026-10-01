package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cms.booking.domain.AppointmentType;
import com.cms.booking.domain.ClinicAppointmentTypePrice;
import com.cms.booking.exception.AppointmentTypeNotFoundException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 017 FR-004, spec US1 AC4: an Appointment Type is never resolved against the wrong doctor. */
class FeeResolutionWrongDoctorTest extends AbstractBookingIntegrationTest {

    @Test
    void resolvingAgainstADifferentDoctorThrowsNotFound() {
        var clinic = saveClinic();
        var doctorA = saveDoctorStaffedAt(clinic);
        var doctorB = saveDoctorStaffedAt(clinic);
        AppointmentType typeForA = appointmentTypeRepository.save(new AppointmentType(doctorA, "Follow-up", null));
        clinicAppointmentTypePriceRepository.save(
                new ClinicAppointmentTypePrice(clinic, typeForA, new BigDecimal("300.00"), null));

        assertThatThrownBy(() -> feeResolutionService.resolve(clinic.getId(), doctorB.getId(), typeForA.getId()))
                .isInstanceOf(AppointmentTypeNotFoundException.class);
    }
}
