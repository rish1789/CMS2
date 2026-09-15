package com.cms.identity.integration;

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
 * T013: a second registration with an already-used admin.email is rejected (FR-012) and
 * creates no new rows. The concurrent variant proves the DB-level unique constraint
 * (uq_account_email) - not merely the fast app-level pre-check - is what actually closes
 * the race (Constitution Principle IV): two truly simultaneous requests with the same
 * email must never both succeed.
 */
class RegisterClinicDuplicateEmailTest extends AbstractIntegrationTest {

    private static final String EMAIL = "duplicate@sunrise-clinic.example";

    @Test
    void secondRegistrationWithSameEmailIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/clinics/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson(EMAIL)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/clinics/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson(EMAIL)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_ALREADY_IN_USE"));

        assertThat(clinicRepository.count()).isEqualTo(1);
        assertThat(accountRepository.count()).isEqualTo(1);
    }

    @Test
    void concurrentRegistrationsWithSameEmailNeverBothSucceed() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> submit = () -> mockMvc.perform(post("/api/v1/clinics/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequestJson("concurrent-" + EMAIL)))
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

            assertThat(successCount).as("exactly one of the two concurrent submissions should succeed")
                    .isEqualTo(1);
            assertThat(accountRepository.count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }
}
