package com.cms.identity.admin.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** T008: a non-Super-Admin actor is rejected 401, zero state change (FR-001a, SC-005). */
class EditDoctorAuthorizationTest extends AbstractAdminIntegrationTest {

    private static final String EDIT_BODY =
            """
            { "specialization": "Radiology", "licenseNumber": "LIC-CHANGED", "experienceYears": 9, "visible": false }
            """;

    @Test
    void editRejectsRequestWithNoCredentials() throws Exception {
        var profile = saveDoctorProfile("doc@example.com", "LIC-ORIGINAL", "ENT", true);

        mockMvc.perform(patch("/api/v1/admin/doctors/{id}", profile.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EDIT_BODY))
                .andExpect(status().isUnauthorized());

        assertUnchanged(profile.getId());
    }

    @Test
    void editRejectsValidStaffAccountCredentials() throws Exception {
        saveStaffAccount("staff@sunrise-clinic.example", "Str0ng!Pass");
        var profile = saveDoctorProfile("doc@example.com", "LIC-ORIGINAL", "ENT", true);

        mockMvc.perform(patch("/api/v1/admin/doctors/{id}", profile.getId())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                basicAuthHeader("staff@sunrise-clinic.example", "Str0ng!Pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EDIT_BODY))
                .andExpect(status().isUnauthorized());

        assertUnchanged(profile.getId());
    }

    private void assertUnchanged(java.util.UUID profileId) {
        var stored = doctorProfileRepository.findById(profileId).orElseThrow();
        assertTrue(stored.isLicenseVerified());
        assertTrue(stored.getLicenseNumber().equals("LIC-ORIGINAL"));
    }
}
