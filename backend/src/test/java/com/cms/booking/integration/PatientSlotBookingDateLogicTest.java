package com.cms.booking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * patient-slot-booking-date-logic: "the present date and upcoming days only" constraint - a
 * past-dated OPEN Slot (its day passed with nobody booking it) is excluded from the listing
 * that decides what a patient can even see, and rejected outright if booked anyway (a stale
 * client, or a direct API call bypassing the listing).
 */
class PatientSlotBookingDateLogicTest extends AbstractPatientBookingIntegrationTest {

    @Test
    void listingExcludesAPastDatedOpenSlot() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var pastSlot = savePastDatedOpenFixedTimeSlot(clinic, doctor);
        var todaySession = saveFixedTimeSessionWithSlots(clinic, doctor);
        var todaySlot = anOpenSlotOf(todaySession);
        var patientAccount = savePatientAccount();

        mockMvc.perform(get("/api/v1/patients/clinics/{clinicId}/slots", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientToken(patientAccount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[?(@.slotId=='" + pastSlot.getId() + "')]").doesNotExist())
                .andExpect(jsonPath("$.slots[?(@.slotId=='" + todaySlot.getId() + "')]").exists());
    }

    @Test
    void bookingAPastDatedSlotDirectlyIsRejected() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var pastSlot = savePastDatedOpenFixedTimeSlot(clinic, doctor);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        var patientAccount = savePatientAccount();

        mockMvc.perform(post("/api/v1/patients/clinics/{clinicId}/slots/{slotId}/book", clinic.getId(), pastSlot.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientToken(patientAccount))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "patientName": "Patient", "appointmentTypeId": "%s" }
                                """
                                        .formatted(appointmentType.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SLOT_DATE_IN_THE_PAST"));
    }

    @Test
    void dateFilterNarrowsListingToExactlyThatDay() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var slot = anOpenSlotOf(session);
        var patientAccount = savePatientAccount();

        mockMvc.perform(get("/api/v1/patients/clinics/{clinicId}/slots", clinic.getId())
                        .param("date", session.getSessionDate().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientToken(patientAccount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[?(@.slotId=='" + slot.getId() + "')]").exists());

        mockMvc.perform(get("/api/v1/patients/clinics/{clinicId}/slots", clinic.getId())
                        .param("date", session.getSessionDate().plusDays(1).toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientToken(patientAccount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[?(@.slotId=='" + slot.getId() + "')]").doesNotExist());
    }
}
