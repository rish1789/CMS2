package com.cms.identity.admin.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** T009: editing an unknown doctorProfileId returns 404, no state change. */
class EditDoctorNotFoundTest extends AbstractAdminIntegrationTest {

    @Test
    void editingAnUnknownDoctorProfileReturnsNotFound() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/doctors/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "specialization": "ENT", "licenseNumber": "LIC-X", "experienceYears": 5, "visible": true }
                                """))
                .andExpect(status().isNotFound());
    }
}
