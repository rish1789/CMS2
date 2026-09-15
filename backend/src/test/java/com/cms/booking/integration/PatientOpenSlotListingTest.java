package com.cms.booking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** 021 FR-011/FR-012, spec US1 AC1: listing returns only currently-OPEN Fixed-Time Slots. */
class PatientOpenSlotListingTest extends AbstractPatientBookingIntegrationTest {

    @Test
    void listsOnlyOpenFixedTimeSlotsWithDoctorAndAppointmentTypeDetail() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var slot = anOpenSlotOf(session);
        saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        var patientAccount = savePatientAccount();
        String token = patientToken(patientAccount);

        mockMvc.perform(get("/api/v1/patients/clinics/{clinicId}/slots", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[?(@.slotId=='" + slot.getId() + "')]").exists())
                .andExpect(jsonPath("$.slots[?(@.slotId=='" + slot.getId() + "')].doctorProfileId")
                        .value(doctor.getId().toString()))
                .andExpect(jsonPath("$.slots[?(@.slotId=='" + slot.getId() + "')].appointmentTypes[0].name")
                        .value("Follow-up"));
    }

    @Test
    void excludesQueueModeSlotsAndSupportsTheDoctorFilter() throws Exception {
        var clinic = saveClinic();
        var doctorA = saveDoctorStaffedAt(clinic);
        var doctorB = saveDoctorStaffedAt(clinic);
        var fixedSession = saveFixedTimeSessionWithSlots(clinic, doctorA);
        var fixedSlot = anOpenSlotOf(fixedSession);
        saveQueueSessionWithOneOpenSlot(clinic, doctorA);
        var otherDoctorSession = saveFixedTimeSessionWithSlots(clinic, doctorB);
        var otherDoctorSlot = anOpenSlotOf(otherDoctorSession);
        var patientAccount = savePatientAccount();
        String token = patientToken(patientAccount);

        mockMvc.perform(get("/api/v1/patients/clinics/{clinicId}/slots", clinic.getId())
                        .queryParam("doctorId", doctorA.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots.length()").value(1))
                .andExpect(jsonPath("$.slots[0].slotId").value(fixedSlot.getId().toString()));

        org.assertj.core.api.Assertions.assertThat(otherDoctorSlot).isNotNull();
    }

    /** pagination-unification-2026-09-10: a small page reports the full totalCount, not just this page's size - a busy Fixed-Time doctor's remaining inventory across a whole booking window is unbounded without one. */
    @Test
    void aSmallPageStillReportsTheFullTotalCount() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor); // 9:00-13:00, 15-min -> 16 open slots
        saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        var patientAccount = savePatientAccount();
        String token = patientToken(patientAccount);

        mockMvc.perform(get("/api/v1/patients/clinics/{clinicId}/slots", clinic.getId())
                        .param("page", "0")
                        .param("size", "5")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots.length()").value(5))
                .andExpect(jsonPath("$.totalCount").value(16));

        org.assertj.core.api.Assertions.assertThat(session).isNotNull();
    }
}
