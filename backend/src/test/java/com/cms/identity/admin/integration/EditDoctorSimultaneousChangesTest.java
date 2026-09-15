package com.cms.identity.admin.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * T010: a request changing the license number AND another field in the same call still
 * resets licenseVerified, and both changes are applied (spec Edge Cases: simultaneous
 * changes don't suppress the rule).
 */
class EditDoctorSimultaneousChangesTest extends AbstractAdminIntegrationTest {

    @Test
    void simultaneousLicenseAndSpecializationChangeStillResets() throws Exception {
        var profile = saveDoctorProfile("doc@example.com", "LIC-ORIGINAL", "ENT", true);

        mockMvc.perform(patch("/api/v1/admin/doctors/{id}", profile.getId())
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "specialization": "Radiology", "licenseNumber": "LIC-CHANGED", "experienceYears": 5, "visible": true }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.specialization").value("Radiology"))
                .andExpect(jsonPath("$.licenseNumber").value("LIC-CHANGED"))
                .andExpect(jsonPath("$.licenseVerified").value(false));

        var stored = doctorProfileRepository.findById(profile.getId()).orElseThrow();
        assertFalse(stored.isLicenseVerified());
        assertEquals("Radiology", stored.getSpecialization());
        assertEquals("LIC-CHANGED", stored.getLicenseNumber());
    }
}
