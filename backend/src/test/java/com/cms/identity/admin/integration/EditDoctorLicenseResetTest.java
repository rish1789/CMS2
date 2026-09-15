package com.cms.identity.admin.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** T004: editing the license number on a verified profile resets licenseVerified to false (FR-003, SC-001). */
class EditDoctorLicenseResetTest extends AbstractAdminIntegrationTest {

    @Test
    void editingLicenseNumberOnVerifiedProfileResetsVerification() throws Exception {
        var profile = saveDoctorProfile("doc@example.com", "LIC-ORIGINAL", "ENT", true);

        mockMvc.perform(patch("/api/v1/admin/doctors/{id}", profile.getId())
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "specialization": "ENT", "licenseNumber": "LIC-CHANGED", "experienceYears": 5, "visible": true }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licenseNumber").value("LIC-CHANGED"))
                .andExpect(jsonPath("$.licenseVerified").value(false));

        assertFalse(doctorProfileRepository.findById(profile.getId()).orElseThrow().isLicenseVerified());
    }
}
