package com.cms.patient.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** T007: happy path - signup creates exactly one active patient_account row. */
class PatientSignupHappyPathTest extends AbstractPatientIntegrationTest {

    @Test
    void signupCreatesExactlyOneActivePatientAccount() throws Exception {
        mockMvc.perform(post("/api/v1/patients/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSignupJson("patient@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientAccountId").exists())
                .andExpect(jsonPath("$.email").value("patient@example.com"));

        assertThat(patientAccountRepository.count()).isEqualTo(1);
        var account = patientAccountRepository.findAll().get(0);
        assertThat(account.isActive()).isTrue();
        assertThat(account.isNotificationOptIn()).isTrue();
        assertThat(account.getPasswordHash()).isNotEqualTo("Str0ng!Pass");
    }
}
