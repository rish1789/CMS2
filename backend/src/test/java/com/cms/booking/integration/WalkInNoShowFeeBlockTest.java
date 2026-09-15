package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.scheduling.SlotStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 025 US2 (P2), FR-001a: a failed fee resolution must leave the original no-show Booking untouched. */
class WalkInNoShowFeeBlockTest extends AbstractWalkInIntegrationTest {

    private ResultActions insert(String clinicId, String sessionId, String token, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/sessions/{sessionId}/walk-in", clinicId, sessionId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void noFeeConfiguredLeavesOriginalNoShowBookingUntouched() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var bufferSlot = aBufferSlotOf(session);
        bufferSlot.setStatus(SlotStatus.BOOKED);
        slotRepository.saveAndFlush(bufferSlot);
        var oldAppointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        var oldPatient = saveExistingPatient(clinic);
        var noShowSlot =
                aNoShowSlotWithOldBooking(session, oldPatient, oldAppointmentType, new BigDecimal("300.00"));
        var oldBooking = bookingRepository.findBySlot_Id(noShowSlot.getId()).orElseThrow();

        var noFeeAppointmentType = saveAppointmentTypeWithNoOverride(doctor);
        String token = clinicAdminToken(clinic);

        insert(clinic.getId().toString(), session.getId().toString(), token,
                        """
                        { "patientName": "Walk-in Patient", "appointmentTypeId": "%s" }
                        """.formatted(noFeeAppointmentType.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NO_FEE_CONFIGURED"));

        assertThat(bookingRepository.findById(oldBooking.getId())).isPresent();
        assertThat(slotRepository.findById(noShowSlot.getId()).orElseThrow().getStatus())
                .isEqualTo(SlotStatus.NO_SHOW);
    }
}
