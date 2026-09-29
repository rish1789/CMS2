package com.cms.scheduling.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import com.cms.scheduling.service.SlotAutoCompletionService;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

/**
 * 057-day-sheet-status-overhaul US1: an APPEARED Slot past its scheduled end time is completed
 * by the automatic sweep with no staff action, and the Session's delay figure reflects the same
 * recalculation a manual completion produces - mirrors SlotCompletionSuccessTest's own
 * fixture/assertion shape for the delay side effect.
 */
class SlotAutoCompletionTest extends AbstractSessionDelayIntegrationTest {

    @Autowired
    private SlotAutoCompletionService slotAutoCompletionService;

    @Test
    void anAppearedSlotPastItsScheduledEndTimeIsAutomaticallyCompleted() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        String token = clinicAdminToken(clinic);
        Slot slot = saveFixedTimeSlotAt(clinic, doctor, LocalTime.now().minusMinutes(30), SlotStatus.APPEARED);

        int completed = slotAutoCompletionService.completeExpiredAppearedSlots();

        assertThat(completed).isEqualTo(1);
        assertThat(slotRepository.findById(slot.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.COMPLETED);

        mockMvc.perform(get(
                        "/api/v1/clinics/{clinicId}/sessions/{sessionId}/delay",
                        clinic.getId(),
                        slot.getSession().getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicable").value(true));
    }

    @Test
    void anAppearedSlotNotYetPastItsScheduledEndTimeIsLeftUntouched() {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Slot slot = saveFixedTimeSlotAt(clinic, doctor, LocalTime.now().plusHours(2), SlotStatus.APPEARED);

        int completed = slotAutoCompletionService.completeExpiredAppearedSlots();

        assertThat(completed).isEqualTo(0);
        assertThat(slotRepository.findById(slot.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.APPEARED);
    }
}
