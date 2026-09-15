package com.cms.patient.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * T025: valid Indian-format mobile stored; invalid format rejected; omitted mobile
 * still succeeds (FR-006).
 */
class PatientSignupMobileValidationTest extends AbstractPatientIntegrationTest {

    @Test
    void validMobileNumberStored() throws Exception {
        mockMvc.perform(post("/api/v1/patients/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "email": "validmobile@example.com", "password": "Str0ng!Pass", "mobile": "9876543210" }
                                """))
                .andExpect(status().isCreated());

        var account = patientAccountRepository.findByEmail("validmobile@example.com").orElseThrow();
        assertThat(account.getMobile()).isEqualTo("9876543210");
    }

    @Test
    void invalidMobileNumberRejected() throws Exception {
        mockMvc.perform(post("/api/v1/patients/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "email": "badmobile@example.com", "password": "Str0ng!Pass", "mobile": "12345" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_MOBILE_NUMBER"));

        assertThat(patientAccountRepository.existsByEmail("badmobile@example.com")).isFalse();
    }

    @Test
    void omittedMobileNumberStillSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/patients/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSignupJson("nomobile@example.com")))
                .andExpect(status().isCreated());

        var account = patientAccountRepository.findByEmail("nomobile@example.com").orElseThrow();
        assertThat(account.getMobile()).isNull();
    }
}
