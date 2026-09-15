package com.cms.identity.admin.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** T009: verify on an unknown doctorProfileId returns 404, no state change. */
class DoctorVerifyNotFoundTest extends AbstractAdminIntegrationTest {

    @Test
    void verifyingAnUnknownDoctorProfileReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/admin/doctors/{id}/verify", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader()))
                .andExpect(status().isNotFound());
    }
}
