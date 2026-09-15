package com.cms.identity.staff.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.clinic.Clinic;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * T011: duplicate email rejected, no rows created; concurrent variant proves the DB
 * constraint (uq_account_email), not just the app-level pre-check, closes the race -
 * same pattern as 001/002's own duplicate-email tests.
 */
class OnboardingDuplicateEmailTest extends AbstractStaffIntegrationTest {

    private static final String EMAIL = "duplicate.hire@sunrise-clinic.example";

    @Test
    void secondOnboardingWithSameEmailIsRejected() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        String token = clinicAdminToken(clinic);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/staff", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validOperationsRequestJson(EMAIL)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/staff", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validOperationsRequestJson(EMAIL)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_ALREADY_IN_USE"));
    }

    @Test
    void concurrentOnboardingsWithSameEmailNeverBothSucceed() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        String token = clinicAdminToken(clinic);
        String concurrentEmail = "concurrent-" + EMAIL;

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> submit = () -> mockMvc.perform(post("/api/v1/clinics/{clinicId}/staff", clinic.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validOperationsRequestJson(concurrentEmail)))
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
        } finally {
            executor.shutdownNow();
        }
    }
}
