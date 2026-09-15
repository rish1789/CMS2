package com.cms.scheduling.integration;

import static org.assertj.core.api.Assertions.assertThat;
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

/** 026 US1 (P1), FR-001/FR-008: completion is rejected for a non-BOOKED Slot or a Queue-mode Session. */
class SlotCompletionRejectionTest extends AbstractSessionDelayIntegrationTest {

    private ResultActions complete(String clinicId, String slotId, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/slots/{slotId}/complete", clinicId, slotId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void completingAnOpenSlotIsRejected() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Slot slot = saveFixedTimeSlotAt(clinic, doctor, LocalTime.now().minusMinutes(15), SlotStatus.OPEN);
        String token = clinicAdminToken(clinic);

        complete(clinic.getId().toString(), slot.getId().toString(), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SLOT_NOT_COMPLETABLE"));

        assertThat(slotRepository.findById(slot.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.OPEN);
    }

    @Test
    void completingAnAlreadyCompletedSlotIsRejectedAgain() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Slot slot = saveFixedTimeSlotAt(clinic, doctor, LocalTime.now().minusMinutes(15), SlotStatus.BOOKED);
        String token = clinicAdminToken(clinic);

        complete(clinic.getId().toString(), slot.getId().toString(), token).andExpect(status().isOk());

        complete(clinic.getId().toString(), slot.getId().toString(), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SLOT_NOT_COMPLETABLE"));
    }

    @Test
    void completingAQueueModeSessionsSlotIsRejected() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Slot slot = saveQueueSlot(clinic, doctor, SlotStatus.BOOKED);
        String token = clinicAdminToken(clinic);

        complete(clinic.getId().toString(), slot.getId().toString(), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NOT_A_FIXED_TIME_SESSION"));

        assertThat(slotRepository.findById(slot.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.BOOKED);
    }
}
