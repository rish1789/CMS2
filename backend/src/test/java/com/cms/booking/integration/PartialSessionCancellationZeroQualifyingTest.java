package com.cms.booking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** 030 US1 (P1), FR-009/SC-004/research.md R2: a cutoff matching nothing is a normal zero-count success, never an error. */
class PartialSessionCancellationZeroQualifyingTest extends AbstractPartialSessionCancellationIntegrationTest {

    @Test
    void cutoffLaterThanEveryRemainingSlotReportsZeroNotAnError() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        List<Slot> slots = slotRepository.findBySession_Id(session.getId());
        Slot slot = slots.stream().filter(s -> s.getStartTime().equals(LocalTime.of(9, 0))).findFirst().orElseThrow();
        bookSlot(clinic, doctor, slot);
        String token = clinicAdminToken(clinic);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/sessions/{sessionId}/cancel-from-cutoff", clinic.getId(), session.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"cutoffTime\": \"23:59:00\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingsCancelled").value(0));
    }
}
