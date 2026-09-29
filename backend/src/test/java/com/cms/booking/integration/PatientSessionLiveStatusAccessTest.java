package com.cms.booking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.domain.Booking;
import com.cms.booking.service.PatientBookingService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 061-doctor-live-status (contracts/doctor-live-status.md, FR-012/research.md Decision 5):
 * end-to-end against a real booking on a real Fixed-Time slot, mirroring
 * QueuePositionPatientAccessTest's own shape for its endpoint - ownership of the Booking, not
 * clinic membership, is the access boundary here too.
 */
class PatientSessionLiveStatusAccessTest extends AbstractPatientBookingIntegrationTest {

    private ResultActions patientLiveStatus(String bookingId, String token) throws Exception {
        return mockMvc.perform(get("/api/v1/patients/bookings/{bookingId}/live-status", bookingId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    void ownerSeesTheApplicableLiveStatusForTheirFixedTimeBooking() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var slot = anOpenSlotOf(session);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        var patientAccount = savePatientAccount();

        Booking booking = patientBookingService.bookSlot(
                patientAccount.getId(),
                clinic.getId(),
                slot.getId(),
                new PatientBookingService.BookSlotInput("Owner", appointmentType.getId()));

        String token = patientToken(patientAccount);

        patientLiveStatus(booking.getId().toString(), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicable").value(true))
                .andExpect(jsonPath("$.bookingId").value(booking.getId().toString()));
    }

    @Test
    void aDifferentPatientsAttemptToQueryIsRejected() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var slot = anOpenSlotOf(session);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        var ownerAccount = savePatientAccount();
        var unrelatedAccount = savePatientAccount();

        Booking booking = patientBookingService.bookSlot(
                ownerAccount.getId(),
                clinic.getId(),
                slot.getId(),
                new PatientBookingService.BookSlotInput("Owner", appointmentType.getId()));

        patientLiveStatus(booking.getId().toString(), patientToken(unrelatedAccount))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("BOOKING_NOT_FOUND"));
    }

    @Test
    void noTokenAtAllIsRejected() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var slot = anOpenSlotOf(session);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        var patientAccount = savePatientAccount();

        Booking booking = patientBookingService.bookSlot(
                patientAccount.getId(),
                clinic.getId(),
                slot.getId(),
                new PatientBookingService.BookSlotInput("Owner", appointmentType.getId()));

        mockMvc.perform(get("/api/v1/patients/bookings/{bookingId}/live-status", booking.getId()))
                .andExpect(status().isUnauthorized());
    }
}
