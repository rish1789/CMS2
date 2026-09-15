package com.cms.identity.admin.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** T018: editing experienceYears alone (license unchanged) on a verified profile leaves licenseVerified true (FR-005, SC-002). */
class EditDoctorOtherFieldsNoResetTest extends AbstractAdminIntegrationTest {

    @Test
    void editingExperienceYearsAloneDoesNotResetVerification() throws Exception {
        var profile = saveDoctorProfile("doc@example.com", "LIC-ORIGINAL", "ENT", true);

        mockMvc.perform(patch("/api/v1/admin/doctors/{id}", profile.getId())
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "specialization": "ENT", "licenseNumber": "LIC-ORIGINAL", "experienceYears": 9, "visible": true }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licenseVerified").value(true))
                .andExpect(jsonPath("$.experienceYears").value(9));

        assertTrue(doctorProfileRepository.findById(profile.getId()).orElseThrow().isLicenseVerified());
    }
}
