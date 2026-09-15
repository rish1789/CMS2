package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.BookingCancelledEvent;
import com.cms.scheduling.Session;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** 029 US1 (P1), FR-003/SC-002: whole-session cancellation never publishes 028's waitlist-bump event, even for multiple Bookings. */
@Import(SessionCancellationNoWaitlistBumpTest.RecordingListenerConfig.class)
class SessionCancellationNoWaitlistBumpTest extends AbstractSessionCancellationIntegrationTest {

    @Autowired
    private RecordingListener recordingListener;

    @Test
    void cancellingASessionWithMultipleBookingsNeverPublishesBookingCancelledEvent() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        List<com.cms.scheduling.Slot> slots = slotRepository.findBySession_Id(session.getId());
        bookSlot(clinic, doctor, slots.get(0));
        bookSlot(clinic, doctor, slots.get(1));
        bookSlot(clinic, doctor, slots.get(2));
        String token = clinicAdminToken(clinic);
        recordingListener.events.clear();

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/sessions/{sessionId}/cancel", clinic.getId(), session.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingsCancelled").value(3));

        assertThat(recordingListener.events).isEmpty();
    }

    @TestConfiguration
    static class RecordingListenerConfig {
        @Bean
        RecordingListener recordingListener() {
            return new RecordingListener();
        }
    }

    static class RecordingListener {
        final List<BookingCancelledEvent> events = new CopyOnWriteArrayList<>();

        @EventListener
        void on(BookingCancelledEvent event) {
            events.add(event);
        }
    }
}
