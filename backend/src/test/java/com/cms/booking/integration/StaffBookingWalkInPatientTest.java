package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 020 FR-003/FR-008, spec US2: walk-in Patient creation as part of the booking flow. */
class StaffBookingWalkInPatientTest extends AbstractStaffBookingIntegrationTest {

    private ResultActions book(String clinicId, String slotId, String token, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/slots/{slotId}/book", clinicId, slotId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void newWalkInPatientWithValidPhoneIsCreatedAndBookingSucceeds() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var slot = anOpenSlotOf(session);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        String token = clinicAdminToken(clinic);

        book(clinic.getId().toString(), slot.getId().toString(), token,
                        """
                        { "patientName": "Walk-in Patient", "patientPhone": "9812345670", "appointmentTypeId": "%s" }
                        """.formatted(appointmentType.getId()))
                .andExpect(status().isCreated());

        var patients = patientRepository.findAll();
        assertThat(patients).hasSize(1);
        assertThat(patients.get(0).getPhone()).isEqualTo("9812345670");
        assertThat(patients.get(0).getPatientAccount()).isNull();
    }

    @Test
    void newWalkInPatientWithNoPhoneStillSucceeds() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var slot = anOpenSlotOf(session);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        String token = clinicAdminToken(clinic);

        book(clinic.getId().toString(), slot.getId().toString(), token,
                        """
                        { "patientName": "Walk-in No Phone", "appointmentTypeId": "%s" }
                        """.formatted(appointmentType.getId()))
                .andExpect(status().isCreated());

        assertThat(patientRepository.findAll()).hasSize(1);
    }

    @Test
    void invalidPhoneRejectsBookingAndCreatesNothing() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var slot = anOpenSlotOf(session);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        String token = clinicAdminToken(clinic);

        book(clinic.getId().toString(), slot.getId().toString(), token,
                        """
                        { "patientName": "Bad Phone", "patientPhone": "12345", "appointmentTypeId": "%s" }
                        """.formatted(appointmentType.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_MOBILE_NUMBER"));

        assertThat(patientRepository.findAll()).isEmpty();
        assertThat(bookingRepository.findAll()).isEmpty();
    }
}
