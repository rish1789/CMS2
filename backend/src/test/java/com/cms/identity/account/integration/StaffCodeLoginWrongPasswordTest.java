package com.cms.identity.account.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.staff.integration.AbstractStaffIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** T002: wrong password with a valid staff code -> 401 INCORRECT_PASSWORD (FR-003; see StaffAuthService for the distinct-error-message decision). */
class StaffCodeLoginWrongPasswordTest extends AbstractStaffIntegrationTest {

    @Test
    void wrongPasswordWithValidStaffCodeReturnsIncorrectPassword() throws Exception {
        saveAccount("wrong.pass.code@sunrise-clinic.example", "Str0ng!Pass", "OP-9003");

        mockMvc.perform(post("/api/v1/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "identifier": "OP-9003", "password": "Incorrect1!" }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INCORRECT_PASSWORD"));
    }
}
