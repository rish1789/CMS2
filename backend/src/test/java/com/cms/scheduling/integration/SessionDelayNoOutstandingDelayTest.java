package com.cms.scheduling.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotStatus;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 026 US1 (P1) / US3 (P1), FR-005/FR-006/SC-005: no-outstanding-delay states, and the doctor's own view access. */
class SessionDelayNoOutstandingDelayTest extends AbstractSessionDelayIntegrationTest {

    private ResultActions delay(String clinicId, String sessionId, String token) throws Exception {
        return mockMvc.perform(get("/api/v1/clinics/{clinicId}/sessions/{sessionId}/delay", clinicId, sessionId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions complete(String clinicId, String slotId, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/slots/{slotId}/complete", clinicId, slotId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void aSessionThatHasNeverHadATriggerShowsNoOutstandingDelay() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Slot slot = saveFixedTimeSlotAt(clinic, doctor, LocalTime.now().minusMinutes(15), SlotStatus.BOOKED);
        String token = clinicAdminToken(clinic);

        delay(clinic.getId().toString(), slot.getSession().getId().toString(), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicable").value(true))
                .andExpect(jsonPath("$.delayMinutes").doesNotExist());
    }

    @Test
    void completingEveryPastDueSlotResultsInNoOutstandingDelay() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Slot onlySlot = saveFixedTimeSlotAt(clinic, doctor, LocalTime.now().minusMinutes(15), SlotStatus.BOOKED);
        String token = clinicAdminToken(clinic);

        complete(clinic.getId().toString(), onlySlot.getId().toString(), token).andExpect(status().isOk());

        delay(clinic.getId().toString(), onlySlot.getSession().getId().toString(), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicable").value(true))
                .andExpect(jsonPath("$.delayMinutes").doesNotExist());
    }

    @Test
    void aDoctorsOwnTokenCanSuccessfullyViewDelay() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Slot slot = saveFixedTimeSlotAt(clinic, doctor, LocalTime.now().minusMinutes(15), SlotStatus.BOOKED);
        String token = doctorToken(doctor);

        delay(clinic.getId().toString(), slot.getSession().getId().toString(), token).andExpect(status().isOk());
    }
}
