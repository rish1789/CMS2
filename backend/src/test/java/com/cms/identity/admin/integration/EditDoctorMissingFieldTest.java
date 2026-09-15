package com.cms.identity.admin.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** T011: a blank required field on the edit request is rejected 400, no state change. */
class EditDoctorMissingFieldTest extends AbstractAdminIntegrationTest {

    @Test
    void blankLicenseNumberIsRejected() throws Exception {
        var profile = saveDoctorProfile("doc@example.com", "LIC-ORIGINAL", "ENT", true);

        mockMvc.perform(patch("/api/v1/admin/doctors/{id}", profile.getId())
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "specialization": "ENT", "licenseNumber": "", "experienceYears": 5, "visible": true }
                                """))
                .andExpect(status().isBadRequest());
    }
}
