package com.cms.identity.account.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.staff.integration.AbstractStaffIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** T007: correct credentials succeed with a token; wrong password and unknown email both return the identically-shaped 401. */
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
    void wrongPasswordRejectedWithSameShapeAsUnknownEmail() throws Exception {
        saveAccount("wrong.pass@sunrise-clinic.example", "Str0ng!Pass", "OP-0002");

        String wrongPasswordBody =
                """
                { "identifier": "wrong.pass@sunrise-clinic.example", "password": "Incorrect1!" }
                """;
        String unknownEmailBody =
                """
                { "identifier": "does.not.exist@sunrise-clinic.example", "password": "Incorrect1!" }
                """;

        mockMvc.perform(post("/api/v1/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrongPasswordBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));

        mockMvc.perform(post("/api/v1/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unknownEmailBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }
}
