package com.cms.patient.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * T009: a second signup with an already-used email is rejected (FR-003) and creates no
 * new row. The concurrent variant proves the DB-level unique constraint
 * (uq_patient_account_email) - not merely the fast app-level pre-check - is what actually
 * closes the race (Constitution Principle IV).
 */
class PatientSignupDuplicateEmailTest extends AbstractPatientIntegrationTest {

    private static final String EMAIL = "duplicate-patient@example.com";

    @Test
    void secondSignupWithSameEmailIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/patients/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSignupJson(EMAIL)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/patients/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validSignupJson(EMAIL)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_ALREADY_IN_USE"));

        assertThat(patientAccountRepository.count()).isEqualTo(1);
    }

    @Test
    void concurrentSignupsWithSameEmailNeverBothSucceed() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> submit = () -> mockMvc.perform(post("/api/v1/patients/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validSignupJson("concurrent-" + EMAIL)))
                    .andReturn()
                    .getResponse()
                    .getStatus();

            List<Future<Integer>> results = executor.invokeAll(List.of(submit, submit));
            long successCount = 0;
            for (Future<Integer> result : results) {
                if (result.get(30, TimeUnit.SECONDS) == 201) {
                    successCount++;
                }
            }

            assertThat(successCount)
                    .as("exactly one of the two concurrent submissions should succeed")
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }
}
