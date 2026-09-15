package com.cms.identity.admin.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** T005: editing the license number on an already-unverified profile leaves it false, no error (US1 AC2). */
class EditDoctorLicenseAlreadyUnverifiedTest extends AbstractAdminIntegrationTest {

    @Test
    void editingLicenseNumberOnUnverifiedProfileStaysUnverified() throws Exception {
        var profile = saveDoctorProfile("doc@example.com", "LIC-ORIGINAL", "ENT", false);

        mockMvc.perform(patch("/api/v1/admin/doctors/{id}", profile.getId())
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "specialization": "ENT", "licenseNumber": "LIC-CHANGED", "experienceYears": 5, "visible": true }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licenseVerified").value(false));

        assertFalse(doctorProfileRepository.findById(profile.getId()).orElseThrow().isLicenseVerified());
    }
}
