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

/** 025 US2 (P2), FR-001a: priority-(2) insertion into a Session's no-show-freed Slot. */
class WalkInNoShowSlotTest extends AbstractWalkInIntegrationTest {

    private ResultActions insert(String clinicId, String sessionId, String token, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/sessions/{sessionId}/walk-in", clinicId, sessionId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void noShowSlotIsUsedWhenNoBufferSlotIsOpenAndOldBookingIsReplaced() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var bufferSlot = aBufferSlotOf(session);
        bufferSlot.setStatus(SlotStatus.BOOKED);
        slotRepository.saveAndFlush(bufferSlot);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        var oldPatient = saveExistingPatient(clinic);
        var noShowSlot = aNoShowSlotWithOldBooking(session, oldPatient, appointmentType, new BigDecimal("300.00"));
        var oldBooking = bookingRepository.findBySlot_Id(noShowSlot.getId()).orElseThrow();
        String token = clinicAdminToken(clinic);

        insert(clinic.getId().toString(), session.getId().toString(), token,
                        """
                        { "patientName": "Walk-in Patient", "appointmentTypeId": "%s" }
                        """.formatted(appointmentType.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slotId").value(noShowSlot.getId().toString()));

        assertThat(bookingRepository.findById(oldBooking.getId())).isEmpty();
        var newBooking = bookingRepository.findBySlot_Id(noShowSlot.getId()).orElseThrow();
        assertThat(newBooking.getId()).isNotEqualTo(oldBooking.getId());
        assertThat(newBooking.getOverrideReason()).isNull();
        assertThat(slotRepository.findById(noShowSlot.getId()).orElseThrow().getStatus())
                .isEqualTo(SlotStatus.BOOKED);
    }

    @Test
    void bufferSlotIsUsedOverNoShowSlotWhenBothAreAvailable() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var bufferSlot = aBufferSlotOf(session);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        var oldPatient = saveExistingPatient(clinic);
        aNoShowSlotWithOldBooking(session, oldPatient, appointmentType, new BigDecimal("300.00"));
        String token = clinicAdminToken(clinic);

        insert(clinic.getId().toString(), session.getId().toString(), token,
                        """
                        { "patientName": "Walk-in Patient", "appointmentTypeId": "%s" }
                        """.formatted(appointmentType.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slotId").value(bufferSlot.getId().toString()));
    }
}
