package com.cms.identity.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * T016: full-stack proof of FR-010 (invalid Indian mobile numbers rejected, with the
 * offending field named) and FR-011 (the field is genuinely optional - omitting it
 * entirely still succeeds).
 */
class RegisterClinicMobileValidationTest extends AbstractIntegrationTest {

    @Test
    void invalidClinicContactMobileRejected() throws Exception {
        String request =
                """
                {
                  "clinic": {
                    "name": "Sunrise Clinic",
                    "address": "12 MG Road, Bengaluru",
                    "contactMobile": "12345"
                  },
                  "admin": {
                    "name": "Dr. Asha Rao",
                    "email": "invalid-mobile@sunrise-clinic.example",
                    "password": "Str0ng!Pass"
                  }
                }
                """;

        mockMvc.perform(post("/api/v1/clinics/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_MOBILE_NUMBER"))
                .andExpect(jsonPath("$.field").value("clinic.contactMobile"));
    }

    @Test
    void invalidAdminMobileRejected() throws Exception {
        String request =
                """
                {
                  "clinic": { "name": "Sunrise Clinic", "address": "12 MG Road, Bengaluru" },
                  "admin": {
                    "name": "Dr. Asha Rao",
                    "email": "invalid-admin-mobile@sunrise-clinic.example",
                    "password": "Str0ng!Pass",
                    "mobile": "5876543210"
                  }
                }
                """;

        mockMvc.perform(post("/api/v1/clinics/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_MOBILE_NUMBER"))
                .andExpect(jsonPath("$.field").value("admin.mobile"));
    }

    @Test
    void omittedMobileNumbersSucceedBecauseFieldIsOptional() throws Exception {
        mockMvc.perform(post("/api/v1/clinics/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson("no-mobile@sunrise-clinic.example")))
                .andExpect(status().isCreated());
    }
}
