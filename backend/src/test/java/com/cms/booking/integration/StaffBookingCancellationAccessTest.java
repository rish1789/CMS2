package com.cms.booking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.Booking;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 028 US1 (P1), FR-001: clinic-scoping and role-agnostic-but-not-role-free staff access, mirrors 027's identical two-case split. */
class StaffBookingCancellationAccessTest extends AbstractBookingCancellationIntegrationTest {

    private ResultActions cancel(String clinicId, String bookingId, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/bookings/{bookingId}/cancel", clinicId, bookingId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void bookingBelongingToADifferentClinicIsNotFound() throws Exception {
        var clinic = saveClinic();
        var otherClinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Booking booking = saveConfirmedBookingAt(clinic, doctor, LocalTime.now().plusHours(3));
        String otherClinicToken = clinicAdminToken(otherClinic);

        cancel(otherClinic.getId().toString(), booking.getId().toString(), otherClinicToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("BOOKING_NOT_FOUND"));
    }

    @Test
    void staffWithNoRoleAtAllAtThisClinicIsForbidden() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Booking booking = saveConfirmedBookingAt(clinic, doctor, LocalTime.now().plusHours(3));

        cancel(clinic.getId().toString(), booking.getId().toString(), unrelatedStaffToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void doctorsOwnTokenCanSuccessfullyCancel() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Booking booking = saveConfirmedBookingAt(clinic, doctor, LocalTime.now().plusHours(3));

        cancel(clinic.getId().toString(), booking.getId().toString(), doctorToken(doctor)).andExpect(status().isOk());
    }
}
