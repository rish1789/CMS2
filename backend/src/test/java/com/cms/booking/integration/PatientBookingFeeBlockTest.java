package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.scheduling.SlotStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** 021 FR-003, spec SC-003: no fee resolvable blocks the booking and creates nothing. */
class PatientBookingFeeBlockTest extends AbstractPatientBookingIntegrationTest {

    @Test
    void noFeeConfiguredBlocksTheBookingAndCreatesNothing() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var slot = anOpenSlotOf(session);
        var appointmentType = saveAppointmentTypeWithNoOverride(doctor); // and no default fee set
        var patientAccount = savePatientAccount();
        String token = patientToken(patientAccount);

        mockMvc.perform(post("/api/v1/patients/clinics/{clinicId}/slots/{slotId}/book", clinic.getId(), slot.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "patientName": "New Patient", "appointmentTypeId": "%s" }
                                """
                                        .formatted(appointmentType.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NO_FEE_CONFIGURED"));

        assertThat(bookingRepository.findAll()).isEmpty();
        assertThat(patientRepository.findAll()).isEmpty(); // blocked before any Patient record was created too
        assertThat(slotRepository.findById(slot.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.OPEN);
    }
}
