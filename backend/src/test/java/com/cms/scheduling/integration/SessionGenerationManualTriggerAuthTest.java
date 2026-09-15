package com.cms.scheduling.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** 015 FR-007/FR-008, spec US2 AC1-AC2: the manual-trigger endpoint is Super-Admin-only. */
class SessionGenerationManualTriggerAuthTest extends AbstractSessionGenerationIntegrationTest {

    @Test
    void superAdminCredentialsSucceedAndReturnTheCreatedCount() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var schedule = saveEveryDaySchedule(clinic, doctor, com.cms.scheduling.ScheduleMode.QUEUE, null);

        mockMvc.perform(post("/api/v1/admin/sessions/generate")
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionsCreated").value(15))
                .andExpect(jsonPath("$.runDate").exists());

        org.assertj.core.api.Assertions.assertThat(sessionRepository.findBySchedule_Id(schedule.getId()))
                .hasSize(15);
    }

    @Test
    void noCredentialsIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/admin/sessions/generate")).andExpect(status().isUnauthorized());
    }

    @Test
    void staffBearerTokenInsteadOfSuperAdminBasicAuthIsUnauthorized() throws Exception {
        var clinic = saveClinic();
        String staffToken = clinicAdminToken(clinic);

        mockMvc.perform(post("/api/v1/admin/sessions/generate")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffToken))
                .andExpect(status().isUnauthorized());
    }
}
