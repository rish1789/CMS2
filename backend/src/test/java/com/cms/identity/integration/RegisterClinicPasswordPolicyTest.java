package com.cms.identity.integration;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * T015: full-stack proof (real HTTP -> real service -> real response) of FR-009 - every
 * failed password rule is listed, not just the first one found. Rule-by-rule logic itself
 * is already exhaustively unit-tested in PasswordPolicyValidatorTest; this test proves the
 * end-to-end wiring (controller -> service -> exception handler -> response body) is
 * correct.
 */
class RegisterClinicPasswordPolicyTest extends AbstractIntegrationTest {

    @Test
    void weakPasswordRejectedWithAllFailedRulesListed() throws Exception {
        String requestWithWeakPassword =
                """
                {
                  "clinic": { "name": "Sunrise Clinic", "address": "12 MG Road, Bengaluru" },
                  "admin": {
                    "name": "Dr. Asha Rao",
                    "email": "weak-password@sunrise-clinic.example",
                    "password": "short"
                  }
                }
                """;

        mockMvc.perform(post("/api/v1/clinics/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestWithWeakPassword))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PASSWORD"))
                .andExpect(jsonPath("$.failedRules").isArray())
                .andExpect(jsonPath(
                        "$.failedRules", hasItems("minLength", "uppercase", "digit", "specialCharacter")));

        org.junit.jupiter.api.Assertions.assertEquals(0, clinicRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(0, accountRepository.count());
    }

    @Test
    void compliantPasswordAccepted() throws Exception {
        mockMvc.perform(post("/api/v1/clinics/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson("strong-password@sunrise-clinic.example")))
                .andExpect(status().isCreated());
    }
}
