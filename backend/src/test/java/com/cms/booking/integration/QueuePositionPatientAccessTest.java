package com.cms.booking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.Booking;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

/** 027 US2 (P2), FR-005/SC-004/SC-005: patient ownership scoping, and staff/patient agreement. */
class QueuePositionPatientAccessTest extends AbstractQueuePositionIntegrationTest {

    private ResultActions staffQueuePosition(String clinicId, String bookingId, String token) throws Exception {
        return mockMvc.perform(get("/api/v1/clinics/{clinicId}/bookings/{bookingId}/queue-position", clinicId, bookingId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions patientQueuePosition(String bookingId, String token) throws Exception {
        return mockMvc.perform(get("/api/v1/patients/bookings/{bookingId}/queue-position", bookingId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    void patientSeesTheIdenticalFigureStaffWouldSeeForTheirOwnBooking() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveQueueSession(clinic, doctor);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        var patientAccount = savePatientAccount();
        String staffToken = clinicAdminToken(clinic);
        var staffAccountId = staffJwtService.validateAndGetAccountId(staffToken).orElseThrow();

        // First token ahead (a walk-in, unowned) stays active.
        staffQueueBookingService.bookSlot(
                staffAccountId, clinic.getId(), session.getId(),
                new com.cms.booking.StaffQueueBookingService.BookSlotInput(null, "Walk-in Ahead", null, appointmentType.getId()));

        Booking patientBooking = patientQueueBookingService.bookSlot(
                patientAccount.getId(), clinic.getId(), session.getId(),
                new com.cms.booking.PatientQueueBookingService.BookSlotInput("Patient Name", appointmentType.getId()));

        String patientToken = patientToken(patientAccount);

        var staffResult = staffQueuePosition(clinic.getId().toString(), patientBooking.getId().toString(), staffToken)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        var patientResult = patientQueuePosition(patientBooking.getId().toString(), patientToken)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.assertj.core.api.Assertions.assertThat(patientResult).isEqualTo(staffResult);
    }

    @Test
    void aDifferentPatientsAttemptToQueryIsRejected() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveQueueSession(clinic, doctor);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        var ownerAccount = savePatientAccount();
        var unrelatedAccount = savePatientAccount();

        Booking booking = patientQueueBookingService.bookSlot(
                ownerAccount.getId(), clinic.getId(), session.getId(),
                new com.cms.booking.PatientQueueBookingService.BookSlotInput("Owner", appointmentType.getId()));

        patientQueuePosition(booking.getId().toString(), patientToken(unrelatedAccount))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("BOOKING_NOT_FOUND"));
    }
}
