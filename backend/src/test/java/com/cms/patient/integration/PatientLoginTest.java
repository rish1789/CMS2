package com.cms.patient.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * T008: correct credentials succeed with a token; wrong password and unknown email now return
 * distinct 401 error codes (see PatientAccountService.authenticate's decision note).
 */
class PatientLoginTest extends AbstractPatientIntegrationTest {

    @Test
    void correctCredentialsSucceedWithToken() throws Exception {
        mockMvc.perform(post("/api/v1/patients/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validSignupJson("login-happy@example.com")));

        mockMvc.perform(post("/api/v1/patients/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "email": "login-happy@example.com", "password": "Str0ng!Pass" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.email").value("login-happy@example.com"));
    }

    @Test
    void wrongPasswordForARegisteredEmailReturnsIncorrectPassword() throws Exception {
        mockMvc.perform(post("/api/v1/patients/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validSignupJson("login-wrongpw@example.com")));

        mockMvc.perform(post("/api/v1/patients/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "email": "login-wrongpw@example.com", "password": "WrongPassword!1" }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INCORRECT_PASSWORD"));
    }

    @Test
    void unknownEmailReturnsAccountNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/patients/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "email": "never-registered@example.com", "password": "Str0ng!Pass" }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("ACCOUNT_NOT_FOUND"));
    }
}
