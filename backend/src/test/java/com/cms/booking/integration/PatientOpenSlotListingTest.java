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
        var queueSession = saveQueueSessionWithOneOpenSlot(clinic, doctorA);
        var queueToken = slotRepository.findBySession_Id(queueSession.getId()).get(0);
        var otherDoctorSession = saveFixedTimeSessionWithSlots(clinic, doctorB);
        var otherDoctorSlot = anOpenSlotOf(otherDoctorSession);
        var patientAccount = savePatientAccount();
        String token = patientToken(patientAccount);

        // Narrowed to the fixed session's day: generation fills a multi-day horizon, so a
        // doctor's whole listing is many sessions' worth of slots. One day = exactly one session.
        mockMvc.perform(get("/api/v1/patients/clinics/{clinicId}/slots", clinic.getId())
                        .queryParam("doctorId", doctorA.getId().toString())
                        .param("date", fixedSession.getSessionDate().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(16))
                .andExpect(jsonPath("$.slots[0].slotId").value(fixedSlot.getId().toString()))
                .andExpect(jsonPath("$.slots[?(@.doctorProfileId != '" + doctorA.getId() + "')]").isEmpty())
                .andExpect(jsonPath("$.slots[?(@.slotId=='" + queueToken.getId() + "')]").doesNotExist())
                .andExpect(jsonPath("$.slots[?(@.slotId=='" + otherDoctorSlot.getId() + "')]").doesNotExist());
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

        // Narrowed to one day (one session) so the total is a known 16, independent of how many
        // days the session generator's horizon spans.
        mockMvc.perform(get("/api/v1/patients/clinics/{clinicId}/slots", clinic.getId())
                        .param("date", session.getSessionDate().toString())
                        .param("page", "0")
                        .param("size", "5")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots.length()").value(5))
                .andExpect(jsonPath("$.totalCount").value(16));
    }
}
