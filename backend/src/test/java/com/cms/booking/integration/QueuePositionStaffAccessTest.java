package com.cms.booking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.Booking;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

/** 027 US1 (P1), FR-004, analyze finding E1: clinic-scoping and role-agnostic-but-not-role-free staff access. */
class QueuePositionStaffAccessTest extends AbstractQueuePositionIntegrationTest {

    private ResultActions queuePosition(String clinicId, String bookingId, String token) throws Exception {
        return mockMvc.perform(get("/api/v1/clinics/{clinicId}/bookings/{bookingId}/queue-position", clinicId, bookingId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private Booking bookToken(
            com.cms.identity.clinic.Clinic clinic,
            com.cms.scheduling.Session session,
            com.cms.booking.AppointmentType appointmentType,
            String callerToken)
            throws Exception {
        var patient = saveExistingPatient(clinic);
        var callerAccountId = staffJwtService.validateAndGetAccountId(callerToken).orElseThrow();
        return staffQueueBookingService.bookSlot(
                callerAccountId, clinic.getId(), session.getId(),
                new com.cms.booking.StaffQueueBookingService.BookSlotInput(patient.getId(), null, null, appointmentType.getId()));
    }

    @Test
    void bookingBelongingToADifferentClinicIsNotFound() throws Exception {
        var clinic = saveClinic();
        var otherClinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveQueueSession(clinic, doctor);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        String ownerToken = clinicAdminToken(clinic);
        String otherClinicToken = clinicAdminToken(otherClinic);

        Booking booking = bookToken(clinic, session, appointmentType, ownerToken);

        queuePosition(otherClinic.getId().toString(), booking.getId().toString(), otherClinicToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("BOOKING_NOT_FOUND"));
    }

    @Test
    void doctorsOwnTokenCanSuccessfullyQuery() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveQueueSession(clinic, doctor);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        String adminToken = clinicAdminToken(clinic);

        Booking booking = bookToken(clinic, session, appointmentType, adminToken);

        queuePosition(clinic.getId().toString(), booking.getId().toString(), doctorToken(doctor)).andExpect(status().isOk());
    }

    @Test
    void staffWithNoRoleAtAllAtThisClinicIsForbidden() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveQueueSession(clinic, doctor);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        String adminToken = clinicAdminToken(clinic);

        Booking booking = bookToken(clinic, session, appointmentType, adminToken);

        queuePosition(clinic.getId().toString(), booking.getId().toString(), unrelatedStaffToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }
}
