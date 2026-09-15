package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotStatus;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 042-day-sheet-hardening follow-up: an optional {@code toTime} bounds cancellation to a
 * mid-session window instead of always running through to the end of the session.
 */
class PartialSessionCancellationRangeTest extends AbstractPartialSessionCancellationIntegrationTest {

    private ResultActions cancelInRange(
            String clinicId, String sessionId, String token, String fromTime, String toTime) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/sessions/{sessionId}/cancel-from-cutoff", clinicId, sessionId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"cutoffTime\": \"" + fromTime + "\", \"toTime\": \"" + toTime + "\" }"));
    }

    @Test
    void toTimeLeavesSlotsAtOrAfterTheUpperBoundUntouched() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        List<Slot> slots = slotRepository.findBySession_Id(session.getId());

        Slot beforeRange = slots.stream().filter(s -> s.getStartTime().equals(LocalTime.of(9, 0))).findFirst().orElseThrow();
        Slot insideRange = slots.stream().filter(s -> s.getStartTime().equals(LocalTime.of(10, 0))).findFirst().orElseThrow();
        Slot atUpperBound = slots.stream().filter(s -> s.getStartTime().equals(LocalTime.of(12, 0))).findFirst().orElseThrow();

        bookSlot(clinic, doctor, beforeRange);
        bookSlot(clinic, doctor, insideRange);
        bookSlot(clinic, doctor, atUpperBound);
        String token = clinicAdminToken(clinic);

        cancelInRange(clinic.getId().toString(), session.getId().toString(), token, "10:00:00", "12:00:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingsCancelled").value(1));

        assertThat(slotRepository.findById(beforeRange.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.BOOKED);
        assertThat(slotRepository.findById(insideRange.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.OPEN);
        // toTime is an exclusive upper bound - a slot starting exactly at it survives, same as
        // fromTime's inclusive lower bound was already established for the existing endpoint.
        assertThat(slotRepository.findById(atUpperBound.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.BOOKED);
    }

    @Test
    void toTimeAtOrBeforeCutoffIsRejectedAsAnInvalidRange() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        String token = clinicAdminToken(clinic);

        cancelInRange(clinic.getId().toString(), session.getId().toString(), token, "11:00:00", "10:00:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_CANCELLATION_RANGE"));
    }
}
