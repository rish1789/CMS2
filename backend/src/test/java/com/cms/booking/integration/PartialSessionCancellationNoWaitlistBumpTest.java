package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.BookingCancelledEvent;
import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import java.time.LocalTime;
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

/** 030 US1 (P1), FR-005/SC-002: partial cancellation never publishes 025's waitlist-bump event, even for multiple Bookings. */
@Import(PartialSessionCancellationNoWaitlistBumpTest.RecordingListenerConfig.class)
class PartialSessionCancellationNoWaitlistBumpTest extends AbstractPartialSessionCancellationIntegrationTest {

    @Autowired
    private RecordingListener recordingListener;

    @Test
    void cancellingFromCutoffWithMultipleBookingsNeverPublishesBookingCancelledEvent() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        List<Slot> slots = slotRepository.findBySession_Id(session.getId());
        Slot slot1 = slots.stream().filter(s -> s.getStartTime().equals(LocalTime.of(11, 0))).findFirst().orElseThrow();
        Slot slot2 = slots.stream().filter(s -> s.getStartTime().equals(LocalTime.of(11, 15))).findFirst().orElseThrow();
        bookSlot(clinic, doctor, slot1);
        bookSlot(clinic, doctor, slot2);
        String token = clinicAdminToken(clinic);
        recordingListener.events.clear();

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/sessions/{sessionId}/cancel-from-cutoff", clinic.getId(), session.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"cutoffTime\": \"11:00:00\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingsCancelled").value(2));

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
