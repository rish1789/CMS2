package com.cms.booking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** 020 FR-001/FR-005, spec US1 AC1-AC2: booking an existing Patient into an OPEN Slot succeeds. */
class StaffBookingExistingPatientTest extends AbstractStaffBookingIntegrationTest {

    @Test
    void bookingExistingPatientSucceedsAndLocksTheResolvedFee() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var slot = anOpenSlotOf(session);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        var patient = saveExistingPatient(clinic);
        String token = clinicAdminToken(clinic);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/slots/{slotId}/book", clinic.getId(), slot.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "patientId": "%s", "appointmentTypeId": "%s" }
                                """
                                        .formatted(patient.getId(), appointmentType.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lockedFee").value(300.00))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.patientId").value(patient.getId().toString()));

        var reloadedSlot = slotRepository.findById(slot.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(reloadedSlot.getStatus())
                .isEqualTo(com.cms.scheduling.SlotStatus.BOOKED);
    }
}
