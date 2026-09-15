package com.cms.identity.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * T006: after a license-number edit resets verification, the profile is absent from
 * 007's findDiscoveryEligible() (SC-004).
 */
class EditDoctorLicenseDiscoveryEligibilityTest extends AbstractAdminIntegrationTest {

    @Test
    void resetProfileIsAbsentFromDiscoveryEligibility() throws Exception {
        var clinic = saveClinic("Sunrise Clinic", true);
        var profile = saveDoctorProfile("doc@example.com", "LIC-ORIGINAL", "ENT", true);
        linkDoctorToClinic(profile, clinic, true);

        assertThat(doctorProfileRepository.findDiscoveryEligible()).contains(profile);

        mockMvc.perform(patch("/api/v1/admin/doctors/{id}", profile.getId())
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "specialization": "ENT", "licenseNumber": "LIC-CHANGED", "experienceYears": 5, "visible": true }
                                """))
                .andExpect(status().isOk());

        assertThat(doctorProfileRepository.findDiscoveryEligible())
                .noneMatch(p -> p.getId().equals(profile.getId()));
    }
}
