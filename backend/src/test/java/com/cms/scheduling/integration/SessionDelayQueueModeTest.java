package com.cms.scheduling.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** 026 US3 (P1, tied with US1), FR-007/SC-004: a Queue-mode Session's delay query is a normal, non-error response - never a numeric value. */
class SessionDelayQueueModeTest extends AbstractSessionDelayIntegrationTest {

    @Test
    void queueModeSessionsDelayQueryIndicatesNotApplicableNotAnError() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Slot slot = saveQueueSlot(clinic, doctor, SlotStatus.OPEN);
        String token = clinicAdminToken(clinic);

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/sessions/{sessionId}/delay", clinic.getId(),
                        slot.getSession().getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicable").value(false))
                .andExpect(jsonPath("$.delayMinutes").doesNotExist());
    }
}
