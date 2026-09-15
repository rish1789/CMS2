package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.scheduling.SlotStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** 020 FR-004, spec US1 AC of "no fee resolvable blocks everything": zero rows of any kind are created. */
class StaffBookingFeeBlockTest extends AbstractStaffBookingIntegrationTest {

    @Test
    void noFeeConfiguredBlocksTheBookingAndCreatesNothing() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var slot = anOpenSlotOf(session);
        var appointmentType = saveAppointmentTypeWithNoOverride(doctor); // and no default fee set
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
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NO_FEE_CONFIGURED"));

        assertThat(bookingRepository.findAll()).isEmpty();
        assertThat(slotRepository.findById(slot.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.OPEN);
    }

    @Test
    void noFeeConfiguredBlocksNewWalkInPatientCreationToo() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var slot = anOpenSlotOf(session);
        var appointmentType = saveAppointmentTypeWithNoOverride(doctor);
        String token = clinicAdminToken(clinic);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/slots/{slotId}/book", clinic.getId(), slot.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "patientName": "New Walk-in", "appointmentTypeId": "%s" }
                                """
                                        .formatted(appointmentType.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NO_FEE_CONFIGURED"));

        assertThat(patientRepository.findAll()).isEmpty();
        assertThat(bookingRepository.findAll()).isEmpty();
    }
}
