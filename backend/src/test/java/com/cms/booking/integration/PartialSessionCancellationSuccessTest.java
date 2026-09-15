package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 030 US1 (P1), FR-002/FR-003/FR-004/FR-008/SC-001/SC-003: cutoff filtering for both Session modes. */
class PartialSessionCancellationSuccessTest extends AbstractPartialSessionCancellationIntegrationTest {

    private ResultActions cancelFromCutoff(String clinicId, String sessionId, String token, String cutoffTime) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/sessions/{sessionId}/cancel-from-cutoff", clinicId, sessionId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"cutoffTime\": \"" + cutoffTime + "\" }"));
    }

    @Test
    void fixedTimeSessionOnlyAffectsAtOrAfterCutoffBookedSlots() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        List<Slot> slots = slotRepository.findBySession_Id(session.getId());

        Slot beforeCutoff = slots.stream().filter(s -> s.getStartTime().equals(LocalTime.of(9, 0))).findFirst().orElseThrow();
        Slot atCutoff = slots.stream().filter(s -> s.getStartTime().equals(LocalTime.of(11, 0))).findFirst().orElseThrow();
        Slot completedBeforeCutoff =
                slots.stream().filter(s -> s.getStartTime().equals(LocalTime.of(9, 15))).findFirst().orElseThrow();
        Slot openAfterCutoff =
                slots.stream().filter(s -> s.getStartTime().equals(LocalTime.of(11, 15))).findFirst().orElseThrow();

        bookSlot(clinic, doctor, beforeCutoff);
        bookSlot(clinic, doctor, atCutoff);
        completedBeforeCutoff.setStatus(SlotStatus.COMPLETED);
        slotRepository.save(completedBeforeCutoff);
        // openAfterCutoff stays OPEN, untouched by construction.

        String token = clinicAdminToken(clinic);

        cancelFromCutoff(clinic.getId().toString(), session.getId().toString(), token, "11:00:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingsCancelled").value(1));

        assertThat(slotRepository.findById(atCutoff.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.OPEN);
        assertThat(slotRepository.findById(beforeCutoff.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.BOOKED);
        assertThat(slotRepository.findById(completedBeforeCutoff.getId()).orElseThrow().getStatus())
                .isEqualTo(SlotStatus.COMPLETED);
        assertThat(slotRepository.findById(openAfterCutoff.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.OPEN);
    }

    @Test
    void queueModeSessionUsesCreatedAtAsTheCutoffProxy() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Session session = saveQueueSession(clinic, doctor);
        Instant sessionDayNoon =
                LocalDate.of(2026, 9, 3).atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant();

        Slot beforeCutoffSlot = addQueueSlotWithCreatedAt(session, 1, sessionDayNoon.minusSeconds(3600));
        Slot afterCutoffSlot = addQueueSlotWithCreatedAt(session, 2, sessionDayNoon.plusSeconds(3600));
        bookSlot(clinic, doctor, beforeCutoffSlot);
        bookSlot(clinic, doctor, afterCutoffSlot);
        String token = clinicAdminToken(clinic);

        cancelFromCutoff(clinic.getId().toString(), session.getId().toString(), token, "12:00:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingsCancelled").value(1));

        assertThat(slotRepository.findById(afterCutoffSlot.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.OPEN);
        assertThat(slotRepository.findById(beforeCutoffSlot.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.BOOKED);
    }
}
