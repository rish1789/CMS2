package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 017 spec Assumptions: setting the default fee twice upserts (replaces), never duplicates. */
class SetDefaultFeeTest extends AbstractBookingIntegrationTest {

    @Test
    void settingDefaultFeeTwiceReplacesRatherThanDuplicates() {
        var doctor = saveDoctorProfile();

        appointmentTypeService.setDefaultFee(doctor.getAccount().getId(), doctor.getId(), new BigDecimal("400.00"));
        appointmentTypeService.setDefaultFee(doctor.getAccount().getId(), doctor.getId(), new BigDecimal("600.00"));

        var all = doctorDefaultFeeRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getAmount()).isEqualByComparingTo("600.00");
    }
}
