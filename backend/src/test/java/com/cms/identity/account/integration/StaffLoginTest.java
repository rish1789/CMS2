package com.cms.identity.account.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.staff.integration.AbstractStaffIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** T007: correct credentials succeed with a token; wrong password and unknown email are now distinguished (see class-level decision note in StaffAuthService). */
class StaffLoginTest extends AbstractStaffIntegrationTest {

    @Test
    void correctCredentialsSucceed() throws Exception {
        saveAccount("login.test@sunrise-clinic.example", "Str0ng!Pass", "OP-0001");

        mockMvc.perform(post("/api/v1/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "identifier": "login.test@sunrise-clinic.example", "password": "Str0ng!Pass" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.accountId").exists())
                .andExpect(jsonPath("$.email").value("login.test@sunrise-clinic.example"))
                // 040-super-admin-rbac-login (US2/SC-004): additive field, destination unchanged.
                .andExpect(jsonPath("$.role").value("STAFF"));
    }

    @Test
    void wrongPasswordForAKnownEmailReturnsIncorrectPassword() throws Exception {
        saveAccount("wrong.pass@sunrise-clinic.example", "Str0ng!Pass", "OP-0002");

        mockMvc.perform(post("/api/v1/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "identifier": "wrong.pass@sunrise-clinic.example", "password": "Incorrect1!" }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INCORRECT_PASSWORD"));
    }

    @Test
    void unknownEmailReturnsAccountNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "identifier": "does.not.exist@sunrise-clinic.example", "password": "Incorrect1!" }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("ACCOUNT_NOT_FOUND"));
    }
}
