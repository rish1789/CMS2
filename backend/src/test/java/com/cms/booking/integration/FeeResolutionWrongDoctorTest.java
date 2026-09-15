package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cms.booking.AppointmentType;
import com.cms.booking.AppointmentTypeNotFoundException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 017 FR-004, spec US1 AC4: an Appointment Type is never resolved against the wrong doctor. */
class FeeResolutionWrongDoctorTest extends AbstractBookingIntegrationTest {

    @Test
    void resolvingAgainstADifferentDoctorThrowsNotFound() {
        var doctorA = saveDoctorProfile();
        var doctorB = saveDoctorProfile();
        AppointmentType typeForA = appointmentTypeRepository.save(
                new AppointmentType(doctorA, "Follow-up", new BigDecimal("300.00")));

        assertThatThrownBy(() -> feeResolutionService.resolve(doctorB.getId(), typeForA.getId()))
                .isInstanceOf(AppointmentTypeNotFoundException.class);
    }
}
