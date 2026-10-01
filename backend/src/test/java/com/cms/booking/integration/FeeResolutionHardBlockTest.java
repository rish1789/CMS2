package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cms.booking.domain.AppointmentType;
import com.cms.booking.domain.ClinicDoctorFee;
import com.cms.booking.exception.NoFeeConfiguredException;
import com.cms.identity.doctor.DoctorProfile;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * 017 FR-003, spec US1 AC3: neither an override nor a default fee -> hard block, never a fabricated
 * amount. 068-per-clinic-fees: evaluated at the booking's clinic - a price at another clinic does
 * not count (FR-004).
 */
class FeeResolutionHardBlockTest extends AbstractBookingIntegrationTest {

    @Test
    void noPriceAtThisClinicThrowsHardBlock() {
        var clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        AppointmentType type = appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", null));

        assertThatThrownBy(() -> feeResolutionService.resolve(clinic.getId(), doctor.getId(), type.getId()))
                .isInstanceOf(NoFeeConfiguredException.class);
    }

    @Test
    void anotherClinicsPriceDoesNotPreventTheHardBlock() {
        var clinicA = saveClinic();
        var clinicB = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinicA);
        linkDoctorToClinic(doctor, clinicB, true);
        AppointmentType type = appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", null));
        clinicDoctorFeeRepository.save(new ClinicDoctorFee(clinicA, doctor, new BigDecimal("500.00"), null));

        assertThatThrownBy(() -> feeResolutionService.resolve(clinicB.getId(), doctor.getId(), type.getId()))
                .isInstanceOf(NoFeeConfiguredException.class);
    }
}
