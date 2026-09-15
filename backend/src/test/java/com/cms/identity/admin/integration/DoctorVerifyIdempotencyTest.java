package com.cms.identity.admin.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** T008: calling verify twice succeeds identically both times, no duplicate side effects (FR-007, SC-005). */
class DoctorVerifyIdempotencyTest extends AbstractAdminIntegrationTest {

    @Test
    void verifyingAnAlreadyVerifiedDoctorIsIdempotent() throws Exception {
        var profile = saveDoctorProfile("doc@example.com", "LIC-001", "ENT", true);

        mockMvc.perform(post("/api/v1/admin/doctors/{id}/verify", profile.getId())
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licenseVerified").value(true));

        mockMvc.perform(post("/api/v1/admin/doctors/{id}/verify", profile.getId())
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licenseVerified").value(true));

        assertTrue(doctorProfileRepository.findById(profile.getId()).orElseThrow().isLicenseVerified());
    }
}
