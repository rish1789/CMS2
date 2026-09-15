package com.cms.identity.staff.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
 * T022: two concurrent onboarding submissions for the same new license number - exactly
 * one creates a Doctor Profile, closed by the DB-level uq_doctor_profile_license_number
 * constraint (Constitution Principle IV), not just the app-level pre-check. Mirrors
 * OnboardingDuplicateEmailTest's concurrent variant for email uniqueness.
 */
class OnboardDoctorLicenseRaceTest extends AbstractStaffIntegrationTest {

    @Test
    void concurrentOnboardingsWithSameNewLicenseNumberNeverBothCreateAProfile() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        String token = clinicAdminToken(clinic);
        String licenseNumber = "LIC-RACE-001";

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> submitA = () -> mockMvc.perform(post("/api/v1/clinics/{clinicId}/staff", clinic.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(doctorRequestJson("dr.race.a@sunrise-clinic.example", "ENT", licenseNumber)))
                    .andReturn()
                    .getResponse()
                    .getStatus();
            Callable<Integer> submitB = () -> mockMvc.perform(post("/api/v1/clinics/{clinicId}/staff", clinic.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(doctorRequestJson("dr.race.b@sunrise-clinic.example", "ENT", licenseNumber)))
                    .andReturn()
                    .getResponse()
                    .getStatus();

            List<Future<Integer>> results = executor.invokeAll(List.of(submitA, submitB));
            long successCount = 0;
            for (Future<Integer> result : results) {
                if (result.get(30, TimeUnit.SECONDS) == 201) {
                    successCount++;
                }
            }

            assertThat(successCount).as("exactly one of the two concurrent submissions should create a profile")
                    .isEqualTo(1);
            assertThat(doctorProfileRepository.count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }
}
