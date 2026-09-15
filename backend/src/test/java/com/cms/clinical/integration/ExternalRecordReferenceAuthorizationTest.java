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
import org.springframework.test.web.servlet.ResultActions;

/** 036 US1 (T011): only the booking's own treating doctor may create/list - no peer-doctor, ClinicAdmin, or Operations override (FR-004). */
class ExternalRecordReferenceAuthorizationTest extends AbstractExternalRecordReferenceIntegrationTest {

    private static final String BODY_JSON =
            "{\"recordType\":\"Lab result\",\"sourceProvider\":\"X\",\"recordDate\":\"2026-08-20\",\"summary\":\"X\"}";

    @Test
    void rejectsADifferentDoctorAtTheSameClinic() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile treatingDoctor = saveDoctorStaffedAt(clinic);
        DoctorProfile otherDoctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, treatingDoctor);
        Booking booking = bookSlot(clinic, treatingDoctor, slotRepository.findBySession_Id(session.getId()).get(0));

        assertForbidden(clinic, booking, doctorToken(otherDoctor));
    }

    @Test
    void rejectsAClinicAdminAtTheSameClinicWithNoOverride() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile treatingDoctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, treatingDoctor);
        Booking booking = bookSlot(clinic, treatingDoctor, slotRepository.findBySession_Id(session.getId()).get(0));

        assertForbidden(clinic, booking, clinicAdminToken(clinic));
    }

    @Test
    void rejectsOperationsStaffAtTheSameClinicWithNoOverride() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile treatingDoctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, treatingDoctor);
        Booking booking = bookSlot(clinic, treatingDoctor, slotRepository.findBySession_Id(session.getId()).get(0));

        assertForbidden(clinic, booking, operationsToken(clinic));
    }

    private void assertForbidden(Clinic clinic, Booking booking, String token) throws Exception {
        ResultActions createResult = mockMvc.perform(post(
                        "/api/v1/clinics/{clinicId}/bookings/{bookingId}/external-record-references",
                        clinic.getId(),
                        booking.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY_JSON));
        createResult.andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FORBIDDEN"));

        mockMvc.perform(get(
                                "/api/v1/clinics/{clinicId}/bookings/{bookingId}/external-record-references",
                                clinic.getId(),
                                booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }
}
