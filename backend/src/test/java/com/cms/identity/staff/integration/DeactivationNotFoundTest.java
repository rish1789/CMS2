package com.cms.identity.staff.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.clinic.Clinic;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** T005: deactivating a nonexistent Role Assignment (unknown accountId at the clinic) returns 404. */
class DeactivationNotFoundTest extends AbstractStaffIntegrationTest {

    @Test
    void returnsNotFoundForUnknownAccount() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        String adminToken = clinicAdminToken(clinic);

        mockMvc.perform(post(
                                "/api/v1/clinics/{clinicId}/staff/{accountId}/deactivate",
                                clinic.getId(),
                                UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }
}
