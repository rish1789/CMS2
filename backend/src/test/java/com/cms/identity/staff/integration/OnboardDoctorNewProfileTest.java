package com.cms.identity.staff.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.clinic.Clinic;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * T018: a license number matching no existing profile behaves exactly like 004's original
 * flow - new Account + DoctorProfile (licenseVerified=false, visible=true) + new
 * credentials, existingAccount=false (unchanged non-regression baseline for T019-T022).
 */
class OnboardDoctorNewProfileTest extends AbstractStaffIntegrationTest {

    @Test
    void newLicenseNumberCreatesNewAccountAndProfile() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        String token = clinicAdminToken(clinic);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/staff", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(doctorRequestJson("dr.new@sunrise-clinic.example", "ENT", "LIC-NEW-001")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.existingAccount").value(false))
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
                .andExpect(jsonPath("$.doctorProfileId").exists());

        assertThat(doctorProfileRepository.count()).isEqualTo(1);
        var profile = doctorProfileRepository.findAll().get(0);
        assertThat(profile.isLicenseVerified()).isFalse();
        assertThat(profile.isVisible()).isTrue();
    }
}
