package com.cms.clinical.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.Booking;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.scheduling.Session;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** 034 US1 (T011): only the booking's own treating doctor may create/read - no peer-doctor or ClinicAdmin override (FR-004). */
class ConsultationNoteAuthorizationTest extends AbstractConsultationNoteIntegrationTest {

    @Test
    void rejectsADifferentDoctorAtTheSameClinic() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile treatingDoctor = saveDoctorStaffedAt(clinic);
        DoctorProfile otherDoctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, treatingDoctor);
        Booking booking = bookSlot(clinic, treatingDoctor, slotRepository.findBySession_Id(session.getId()).get(0));

        mockMvc.perform(post(
                                "/api/v1/clinics/{clinicId}/bookings/{bookingId}/consultation-notes",
                                clinic.getId(),
                                booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorToken(otherDoctor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"...\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        mockMvc.perform(get(
                                "/api/v1/clinics/{clinicId}/bookings/{bookingId}/consultation-notes",
                                clinic.getId(),
                                booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorToken(otherDoctor)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void rejectsAClinicAdminAtTheSameClinicWithNoOverride() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile treatingDoctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, treatingDoctor);
        Booking booking = bookSlot(clinic, treatingDoctor, slotRepository.findBySession_Id(session.getId()).get(0));

        mockMvc.perform(post(
                                "/api/v1/clinics/{clinicId}/bookings/{bookingId}/consultation-notes",
                                clinic.getId(),
                                booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"...\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        mockMvc.perform(get(
                                "/api/v1/clinics/{clinicId}/bookings/{bookingId}/consultation-notes",
                                clinic.getId(),
                                booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }
}
