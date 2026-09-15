package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** 030 US1 (P1), FR-006/SC-005: only Bookings with a linked Patient Account feed the notification pipeline. */
class PartialSessionCancellationNotificationTest extends AbstractPartialSessionCancellationIntegrationTest {

    @Test
    void onlyTheLinkedPatientsBookingProducesANotificationEvent() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        List<Slot> slots = slotRepository.findBySession_Id(session.getId());
        Slot linkedSlot = slots.stream().filter(s -> s.getStartTime().equals(LocalTime.of(11, 0))).findFirst().orElseThrow();
        Slot walkInSlot = slots.stream().filter(s -> s.getStartTime().equals(LocalTime.of(11, 15))).findFirst().orElseThrow();
        var patientAccount = savePatientAccount();

        bookSlot(clinic, doctor, linkedSlot, patientAccount);
        bookSlot(clinic, doctor, walkInSlot, null);
        String token = clinicAdminToken(clinic);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/sessions/{sessionId}/cancel-from-cutoff", clinic.getId(), session.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"cutoffTime\": \"11:00:00\" }"))
                .andExpect(status().isOk());

        var events = notificationEventRepository.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getPatientAccount().getId()).isEqualTo(patientAccount.getId());
    }
}
