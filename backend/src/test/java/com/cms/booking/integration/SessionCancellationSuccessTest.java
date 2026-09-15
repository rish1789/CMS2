package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 029 US1 (P1), FR-001/FR-002/FR-004/FR-008/SC-001/SC-003: bulk cancellation with mixed Slot states, both Session modes. */
class SessionCancellationSuccessTest extends AbstractSessionCancellationIntegrationTest {

    private ResultActions cancel(String clinicId, String sessionId, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/sessions/{sessionId}/cancel", clinicId, sessionId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void fixedTimeSessionWithMixedSlotStatesCancelsOnlyTheActiveBookings() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        List<Slot> slots = slotRepository.findBySession_Id(session.getId());

        // Book 3, leave 2 OPEN, mark 1 NO_SHOW and 1 COMPLETED (untouched by this feature).
        bookSlot(clinic, doctor, slots.get(0));
        bookSlot(clinic, doctor, slots.get(1));
        bookSlot(clinic, doctor, slots.get(2));
        slots.get(3).setStatus(SlotStatus.NO_SHOW);
        slotRepository.save(slots.get(3));
        slots.get(4).setStatus(SlotStatus.COMPLETED);
        slotRepository.save(slots.get(4));
        // slots.get(5), slots.get(6)... remain OPEN untouched.

        String token = clinicAdminToken(clinic);

        cancel(clinic.getId().toString(), session.getId().toString(), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingsCancelled").value(3));

        assertThat(slotRepository.findById(slots.get(0).getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.OPEN);
        assertThat(slotRepository.findById(slots.get(1).getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.OPEN);
        assertThat(slotRepository.findById(slots.get(2).getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.OPEN);
        assertThat(slotRepository.findById(slots.get(3).getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.NO_SHOW);
        assertThat(slotRepository.findById(slots.get(4).getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.COMPLETED);
        assertThat(slotRepository.findById(slots.get(5).getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.OPEN);
    }

    @Test
    void queueModeSessionCancelsAllActiveBookings() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Session session = saveQueueSession(clinic, doctor);
        var slot1 = addQueueSlot(session, 1);
        var slot2 = addQueueSlot(session, 2);
        bookSlot(clinic, doctor, slot1);
        bookSlot(clinic, doctor, slot2);
        String token = clinicAdminToken(clinic);

        cancel(clinic.getId().toString(), session.getId().toString(), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingsCancelled").value(2));

        assertThat(slotRepository.findById(slot1.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.OPEN);
        assertThat(slotRepository.findById(slot2.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.OPEN);
    }
}
