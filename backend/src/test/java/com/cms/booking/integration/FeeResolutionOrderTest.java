package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.booking.AppointmentType;
import com.cms.booking.DoctorDefaultFee;
import com.cms.identity.doctor.DoctorProfile;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 017 FR-001/FR-002, spec US1 AC1-AC2: override wins; otherwise falls through to the doctor's default fee. */
class FeeResolutionOrderTest extends AbstractBookingIntegrationTest {

    @Test
    void overrideWinsRegardlessOfDefaultFee() {
        DoctorProfile doctor = saveDoctorProfile();
        AppointmentType type = appointmentTypeRepository.save(
                new AppointmentType(doctor, "Follow-up", new BigDecimal("300.00")));
        doctorDefaultFeeRepository.save(new DoctorDefaultFee(doctor, new BigDecimal("500.00")));

        BigDecimal resolved = feeResolutionService.resolve(doctor.getId(), type.getId());

        assertThat(resolved).isEqualByComparingTo("300.00");
    }

    @Test
    void noOverrideFallsThroughToDoctorDefaultFee() {
        DoctorProfile doctor = saveDoctorProfile();
        AppointmentType type = appointmentTypeRepository.save(new AppointmentType(doctor, "New Patient", null));
        doctorDefaultFeeRepository.save(new DoctorDefaultFee(doctor, new BigDecimal("500.00")));

        BigDecimal resolved = feeResolutionService.resolve(doctor.getId(), type.getId());

        assertThat(resolved).isEqualByComparingTo("500.00");
    }
}
