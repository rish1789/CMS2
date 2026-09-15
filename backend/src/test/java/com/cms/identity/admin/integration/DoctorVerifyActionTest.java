package com.cms.identity.admin.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** T006: verify flips licenseVerified false -> true (FR-005). */
class DoctorVerifyActionTest extends AbstractAdminIntegrationTest {

    @Test
    void verifyingAnUnverifiedDoctorSetsLicenseVerifiedTrue() throws Exception {
        var profile = saveDoctorProfile("doc@example.com", "LIC-001", "ENT", false);

        mockMvc.perform(post("/api/v1/admin/doctors/{id}/verify", profile.getId())
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctorProfileId").value(profile.getId().toString()))
                .andExpect(jsonPath("$.licenseVerified").value(true));

        assertTrue(doctorProfileRepository.findById(profile.getId()).orElseThrow().isLicenseVerified());
    }
}
