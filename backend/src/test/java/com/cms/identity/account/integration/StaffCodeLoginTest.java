package com.cms.identity.account.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.staff.integration.AbstractStaffIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** T001: staff-code login resolves to the same Account/session as email login (FR-002). */
class StaffCodeLoginTest extends AbstractStaffIntegrationTest {

    @Test
    void staffCodeLoginSucceedsIdenticallyToEmailLogin() throws Exception {
        saveAccount("code.login@sunrise-clinic.example", "Str0ng!Pass", "DR-9001");

        mockMvc.perform(post("/api/v1/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "identifier": "DR-9001", "password": "Str0ng!Pass" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.accountId").exists())
                .andExpect(jsonPath("$.email").value("code.login@sunrise-clinic.example"));
    }

    @Test
    void emailLoginStillWorksWithRenamedIdentifierField() throws Exception {
        saveAccount("still.works@sunrise-clinic.example", "Str0ng!Pass", "OP-9002");

        mockMvc.perform(post("/api/v1/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "identifier": "still.works@sunrise-clinic.example", "password": "Str0ng!Pass" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("still.works@sunrise-clinic.example"));
    }
}
