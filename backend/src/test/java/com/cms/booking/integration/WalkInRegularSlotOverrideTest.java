package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotStatus;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 025 US3 (P3), FR-003/FR-004/FR-009: priority-(3) insertion into a regular Slot requires a written override reason. */
class WalkInRegularSlotOverrideTest extends AbstractWalkInIntegrationTest {

    private ResultActions insert(String clinicId, String sessionId, String token, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/sessions/{sessionId}/walk-in", clinicId, sessionId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** Books every Slot in the Session except one, leaving that one as the sole regular OPEN candidate (no buffer, no no-show). */
    private Slot leaveOneRegularOpenSlot(com.cms.identity.clinic.Clinic clinic, com.cms.scheduling.Session session) {
        List<Slot> slots = slotsOf(session);
        Slot bufferSlot = aBufferSlotOf(session);
        bufferSlot.setStatus(SlotStatus.BOOKED);
        slotRepository.saveAndFlush(bufferSlot);

        Slot kept = slots.stream().filter(s -> !s.isBuffer()).findFirst().orElseThrow();
        for (Slot slot : slots) {
            if (slot.getId().equals(bufferSlot.getId()) || slot.getId().equals(kept.getId())) {
                continue;
            }
            slot.setStatus(SlotStatus.BOOKED);
        }
        slotRepository.saveAllAndFlush(slots);
        return kept;
    }

    @Test
    void regularSlotWithoutOverrideReasonIsRejectedThenSucceedsWithOne() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var regularSlot = leaveOneRegularOpenSlot(clinic, session);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        String token = clinicAdminToken(clinic);

        insert(clinic.getId().toString(), session.getId().toString(), token,
                        """
                        { "patientName": "Walk-in Patient", "appointmentTypeId": "%s" }
                        """.formatted(appointmentType.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("OVERRIDE_REASON_REQUIRED"));

        assertThat(bookingRepository.findAll()).isEmpty();

        insert(clinic.getId().toString(), session.getId().toString(), token,
                        """
                        { "patientName": "Walk-in Patient", "appointmentTypeId": "%s", "overrideReason": "Family emergency, doctor agreed to fit them in" }
                        """.formatted(appointmentType.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slotId").value(regularSlot.getId().toString()));

        var booking = bookingRepository.findBySlot_Id(regularSlot.getId()).orElseThrow();
        assertThat(booking.getOverrideReason()).isEqualTo("Family emergency, doctor agreed to fit them in");
    }

    @Test
    void allSlotsExhaustedRejectsAsNoSlotAvailableRegardlessOfOverrideReason() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        List<Slot> slots = slotsOf(session);
        for (Slot slot : slots) {
            slot.setStatus(SlotStatus.BOOKED);
        }
        slotRepository.saveAllAndFlush(slots);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        String token = clinicAdminToken(clinic);

        insert(clinic.getId().toString(), session.getId().toString(), token,
                        """
                        { "patientName": "Walk-in Patient", "appointmentTypeId": "%s", "overrideReason": "Anything" }
                        """.formatted(appointmentType.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NO_SLOT_AVAILABLE"));
    }
}
