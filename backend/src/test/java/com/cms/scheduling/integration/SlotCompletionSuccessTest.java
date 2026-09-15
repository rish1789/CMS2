package com.cms.scheduling.integration;

import static org.assertj.core.api.Assertions.assertThat;
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

/** 026 US1 (P1), FR-001/FR-002/FR-004: completing a BOOKED Slot recalculates and exposes the Session's delay. */
class SlotCompletionSuccessTest extends AbstractSessionDelayIntegrationTest {

    private ResultActions complete(String clinicId, String slotId, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/slots/{slotId}/complete", clinicId, slotId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON));
    }

    private ResultActions delay(String clinicId, String sessionId, String token) throws Exception {
        return mockMvc.perform(get("/api/v1/clinics/{clinicId}/sessions/{sessionId}/delay", clinicId, sessionId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    void completingABookedSlotRecalculatesDelayToTheEarliestStillUnresolvedPastDueSlot() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        String token = clinicAdminToken(clinic);

        // The Slot being completed: 15 minutes ago, BOOKED - saveFixedTimeSlotAt creates its own
        // fresh Session, which the second Slot below is then added into.
        Slot completingSlot = saveFixedTimeSlotAt(clinic, doctor, LocalTime.now().minusMinutes(15), SlotStatus.BOOKED);
        // Earliest still-unresolved past-due Slot in the SAME Session: 30 minutes ago, still BOOKED.
        Slot earlierSlot = addSlotAt(completingSlot.getSession(), LocalTime.now().minusMinutes(30), SlotStatus.BOOKED);
        String clinicId = clinic.getId().toString();
        String sessionId = completingSlot.getSession().getId().toString();

        complete(clinicId, completingSlot.getId().toString(), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        delay(clinicId, sessionId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicable").value(true))
                .andExpect(jsonPath("$.delayMinutes").value(org.hamcrest.Matchers.greaterThanOrEqualTo(30)));

        assertThat(slotRepository.findById(earlierSlot.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.BOOKED);
    }
}
