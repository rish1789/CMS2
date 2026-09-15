package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.scheduling.Session;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** 029 US1 (P1), FR-006/SC-005: only Bookings with a linked Patient Account feed the notification pipeline. */
class SessionCancellationNotificationTest extends AbstractSessionCancellationIntegrationTest {

    @Test
    void onlyTheLinkedPatientsBookingProducesANotificationEvent() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        List<com.cms.scheduling.Slot> slots = slotRepository.findBySession_Id(session.getId());
        var patientAccount = savePatientAccount();

        bookSlot(clinic, doctor, slots.get(0), patientAccount);
        bookSlot(clinic, doctor, slots.get(1), null);
        String token = clinicAdminToken(clinic);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/sessions/{sessionId}/cancel", clinic.getId(), session.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        var events = notificationEventRepository.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getPatientAccount().getId()).isEqualTo(patientAccount.getId());
    }
}
