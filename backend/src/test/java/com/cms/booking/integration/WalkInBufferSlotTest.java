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

/** 025 US1 (P1): priority-(1) insertion into a Session's OPEN buffer Slot. */
class WalkInBufferSlotTest extends AbstractWalkInIntegrationTest {

    private ResultActions insert(String clinicId, String sessionId, String token, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/sessions/{sessionId}/walk-in", clinicId, sessionId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void bufferSlotInsertionSucceedsWithNoOverrideReasonAndCreatesWalkInPatient() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var bufferSlot = aBufferSlotOf(session);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        String token = clinicAdminToken(clinic);

        insert(clinic.getId().toString(), session.getId().toString(), token,
                        """
                        { "patientName": "Walk-in Patient", "patientPhone": "9812345670", "appointmentTypeId": "%s" }
                        """.formatted(appointmentType.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slotId").value(bufferSlot.getId().toString()))
                .andExpect(jsonPath("$.lockedFee").value(300.00));

        assertThat(slotRepository.findById(bufferSlot.getId()).orElseThrow().getStatus())
                .isEqualTo(SlotStatus.BOOKED);
        assertThat(patientRepository.findAll()).hasSize(1);
        assertThat(patientRepository.findAll().get(0).getPhone()).isEqualTo("9812345670");
    }

    @Test
    void invalidPhoneRejectsInsertionAndCreatesNothing() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        String token = clinicAdminToken(clinic);

        insert(clinic.getId().toString(), session.getId().toString(), token,
                        """
                        { "patientName": "Bad Phone", "patientPhone": "12345", "appointmentTypeId": "%s" }
                        """.formatted(appointmentType.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_MOBILE_NUMBER"));

        assertThat(patientRepository.findAll()).isEmpty();
        assertThat(bookingRepository.findAll()).isEmpty();
    }

    @Test
    void absentPhoneStillSucceeds() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        String token = clinicAdminToken(clinic);

        insert(clinic.getId().toString(), session.getId().toString(), token,
                        """
                        { "patientName": "Contact-less Walk-in", "appointmentTypeId": "%s" }
                        """.formatted(appointmentType.getId()))
                .andExpect(status().isCreated());

        assertThat(patientRepository.findAll()).hasSize(1);
    }

    @Test
    void sessionWithNoOpenBufferSlotFallsThroughToARegularOpenSlot() throws Exception {
        // With the full priority search wired (tiers 1-3 all implemented in WalkInInsertionService -
        // see Phase 3/4/5's implementation notes in tasks.md), closing off the buffer tier alone
        // still leaves regular OPEN Slots as a tier-3 candidate, gated behind an override reason.
        // NO_SLOT_AVAILABLE itself is covered end-to-end (every tier exhausted) by
        // WalkInRegularSlotOverrideTest's allSlotsExhaustedRejectsAsNoSlotAvailable.
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var bufferSlot = aBufferSlotOf(session);
        bufferSlot.setStatus(SlotStatus.BOOKED);
        slotRepository.saveAndFlush(bufferSlot);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        String token = clinicAdminToken(clinic);

        insert(clinic.getId().toString(), session.getId().toString(), token,
                        """
                        { "patientId": "%s", "appointmentTypeId": "%s" }
                        """.formatted(saveExistingPatient(clinic).getId(), appointmentType.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("OVERRIDE_REASON_REQUIRED"));
    }
}
