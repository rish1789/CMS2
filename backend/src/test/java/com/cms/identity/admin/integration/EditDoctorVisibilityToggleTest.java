package com.cms.identity.admin.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * T019: editing visible alone (license unchanged) on a verified profile leaves
 * licenseVerified true, and visible reflects the new value (FR-005; closes 007's
 * deferred write-gap for the visible toggle).
 */
class EditDoctorVisibilityToggleTest extends AbstractAdminIntegrationTest {

    @Test
    void editingVisibleAloneDoesNotResetVerificationAndUpdatesVisible() throws Exception {
        var profile = saveDoctorProfile("doc@example.com", "LIC-ORIGINAL", "ENT", true);
        assertTrue(profile.isVisible());

        mockMvc.perform(patch("/api/v1/admin/doctors/{id}", profile.getId())
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "specialization": "ENT", "licenseNumber": "LIC-ORIGINAL", "experienceYears": 5, "visible": false }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licenseVerified").value(true))
                .andExpect(jsonPath("$.visible").value(false));

        var stored = doctorProfileRepository.findById(profile.getId()).orElseThrow();
        assertTrue(stored.isLicenseVerified());
        assertFalse(stored.isVisible());
    }
}
