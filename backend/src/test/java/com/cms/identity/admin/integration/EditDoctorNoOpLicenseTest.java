package com.cms.identity.admin.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * T020: resubmitting the identical license number alongside a specialization change
 * leaves licenseVerified unchanged (FR-004, edge case: no-op license value).
 */
class EditDoctorNoOpLicenseTest extends AbstractAdminIntegrationTest {

    @Test
    void resubmittingTheSameLicenseNumberDoesNotReset() throws Exception {
        var profile = saveDoctorProfile("doc@example.com", "LIC-ORIGINAL", "ENT", true);

        mockMvc.perform(patch("/api/v1/admin/doctors/{id}", profile.getId())
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "specialization": "Radiology", "licenseNumber": "LIC-ORIGINAL", "experienceYears": 5, "visible": true }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.specialization").value("Radiology"))
                .andExpect(jsonPath("$.licenseVerified").value(true));

        assertTrue(doctorProfileRepository.findById(profile.getId()).orElseThrow().isLicenseVerified());
    }
}
