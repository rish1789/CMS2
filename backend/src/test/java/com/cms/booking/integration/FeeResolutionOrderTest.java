package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.booking.domain.AppointmentType;
import com.cms.booking.domain.ClinicAppointmentTypePrice;
import com.cms.booking.domain.ClinicDoctorFee;
import com.cms.identity.doctor.DoctorProfile;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * 017 FR-001/FR-002, spec US1 AC1-AC2: the type's price wins; otherwise falls through to the
 * doctor's default fee. 068-per-clinic-fees: both are the booking clinic's own prices.
 */
class FeeResolutionOrderTest extends AbstractBookingIntegrationTest {

    @Test
    void clinicTypePriceWinsRegardlessOfTheClinicDefaultFee() {
        var clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        AppointmentType type = appointmentTypeRepository.save(new AppointmentType(doctor, "Follow-up", null));
        clinicAppointmentTypePriceRepository.save(
                new ClinicAppointmentTypePrice(clinic, type, new BigDecimal("300.00"), null));
        clinicDoctorFeeRepository.save(new ClinicDoctorFee(clinic, doctor, new BigDecimal("500.00"), null));

        BigDecimal resolved = feeResolutionService.resolve(clinic.getId(), doctor.getId(), type.getId());

        assertThat(resolved).isEqualByComparingTo("300.00");
    }

    @Test
    void noTypePriceFallsThroughToTheClinicDefaultFee() {
        var clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        AppointmentType type = appointmentTypeRepository.save(new AppointmentType(doctor, "New Patient", null));
        clinicDoctorFeeRepository.save(new ClinicDoctorFee(clinic, doctor, new BigDecimal("500.00"), null));

        BigDecimal resolved = feeResolutionService.resolve(clinic.getId(), doctor.getId(), type.getId());

        assertThat(resolved).isEqualByComparingTo("500.00");
    }
}
