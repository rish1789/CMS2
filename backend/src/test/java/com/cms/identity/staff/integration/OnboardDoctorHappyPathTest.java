package com.cms.identity.staff.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.clinic.Clinic;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** T026: Doctor onboarding creates a DoctorProfile in the same transaction, license_verified=false (FR-007, SC-004). */
class OnboardDoctorHappyPathTest extends AbstractStaffIntegrationTest {

    @Test
    void onboardsDoctorWithProfile() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        String token = clinicAdminToken(clinic);

        MvcResult result = mockMvc.perform(post("/api/v1/clinics/{clinicId}/staff", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validDoctorRequestJson("dr.hire@sunrise-clinic.example")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("Doctor"))
                .andExpect(jsonPath("$.doctorProfileId").exists())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("\"doctorProfileId\":null");
        assertThat(doctorProfileRepository.count()).isEqualTo(1);
        var profile = doctorProfileRepository.findAll().get(0);
        assertThat(profile.isLicenseVerified()).isFalse();
        assertThat(profile.getSpecialization()).isEqualTo("Cardiology");
    }
}
