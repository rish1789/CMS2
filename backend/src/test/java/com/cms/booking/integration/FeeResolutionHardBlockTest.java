package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cms.booking.AppointmentType;
import com.cms.booking.NoFeeConfiguredException;
import com.cms.identity.doctor.DoctorProfile;
import org.junit.jupiter.api.Test;

/** 017 FR-003, spec US1 AC3: neither an override nor a default fee -> hard block, never a fabricated amount. */
class FeeResolutionHardBlockTest extends AbstractBookingIntegrationTest {

    @Test
    void noOverrideAndNoDefaultFeeThrowsHardBlock() {
        DoctorProfile doctor = saveDoctorProfile();
        AppointmentType type = appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", null));

        assertThatThrownBy(() -> feeResolutionService.resolve(doctor.getId(), type.getId()))
                .isInstanceOf(NoFeeConfiguredException.class);
    }
}
