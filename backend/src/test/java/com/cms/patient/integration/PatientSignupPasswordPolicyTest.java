package com.cms.patient.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** T011: each individual password-policy rule violation is rejected, listing every failed rule (FR-002). */
class PatientSignupPasswordPolicyTest extends AbstractPatientIntegrationTest {

    @Test
    void weakPasswordRejectedWithAllFailedRulesListed() throws Exception {
        mockMvc.perform(post("/api/v1/patients/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "email": "weakpw@example.com", "password": "short" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PASSWORD"))
                .andExpect(jsonPath("$.failedRules").isArray())
                .andExpect(jsonPath("$.failedRules", org.hamcrest.Matchers.hasItem("minLength")))
                .andExpect(jsonPath("$.failedRules", org.hamcrest.Matchers.hasItem("uppercase")))
                .andExpect(jsonPath("$.failedRules", org.hamcrest.Matchers.hasItem("digit")))
                .andExpect(jsonPath("$.failedRules", org.hamcrest.Matchers.hasItem("specialCharacter")));
    }

    @Test
    void passwordMissingOnlySpecialCharacterRejected() throws Exception {
        mockMvc.perform(post("/api/v1/patients/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "email": "nospecial@example.com", "password": "Password123" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PASSWORD"))
                .andExpect(jsonPath("$.failedRules", org.hamcrest.Matchers.contains("specialCharacter")));
    }
}
