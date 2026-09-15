package com.cms.booking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.Booking;
import com.cms.scheduling.SlotStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

/** 027 US1 (P1), FR-001/FR-002/SC-001/SC-002: the spec's own worked example, and dynamic recalculation. */
class QueuePositionSuccessTest extends AbstractQueuePositionIntegrationTest {

    private ResultActions queuePosition(String clinicId, String bookingId, String token) throws Exception {
        return mockMvc.perform(get("/api/v1/clinics/{clinicId}/bookings/{bookingId}/queue-position", clinicId, bookingId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    void workedExampleFromSpecAndDynamicRecalculationAfterANoShow() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveQueueSession(clinic, doctor);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        String token = clinicAdminToken(clinic);
        String clinicId = clinic.getId().toString();

        Booking token1 = bookToken(clinic, session, appointmentType, token);
        Booking token2 = bookToken(clinic, session, appointmentType, token);
        Booking token3 = bookToken(clinic, session, appointmentType, token);
        Booking token4 = bookToken(clinic, session, appointmentType, token);
        Booking token5 = bookToken(clinic, session, appointmentType, token);

        setSlotStatus(token1, SlotStatus.COMPLETED);
        setSlotStatus(token2, SlotStatus.COMPLETED);
        // token3 stays BOOKED ("in progress"), token4 stays BOOKED.

        queuePosition(clinicId, token5.getId().toString(), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicable").value(true))
                .andExpect(jsonPath("$.position").value(3));

        setSlotStatus(token3, SlotStatus.NO_SHOW);

        queuePosition(clinicId, token5.getId().toString(), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(2));
    }

    @Test
    void noTokensAheadYieldsPositionOne() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveQueueSession(clinic, doctor);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        String token = clinicAdminToken(clinic);

        Booking firstToken = bookToken(clinic, session, appointmentType, token);

        queuePosition(clinic.getId().toString(), firstToken.getId().toString(), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(1));
    }

    @Test
    void everyTokenAheadAlreadyResolvedYieldsPositionOne() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveQueueSession(clinic, doctor);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        String token = clinicAdminToken(clinic);

        Booking token1 = bookToken(clinic, session, appointmentType, token);
        Booking token2 = bookToken(clinic, session, appointmentType, token);
        Booking token3 = bookToken(clinic, session, appointmentType, token);

        setSlotStatus(token1, SlotStatus.COMPLETED);
        setSlotStatus(token2, SlotStatus.NO_SHOW);

        queuePosition(clinic.getId().toString(), token3.getId().toString(), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(1));
    }

    private Booking bookToken(
            com.cms.identity.clinic.Clinic clinic,
            com.cms.scheduling.Session session,
            com.cms.booking.AppointmentType appointmentType,
            String token)
            throws Exception {
        var patient = saveExistingPatient(clinic);
        return staffQueueBookingService.bookSlot(
                accountIdFor(token),
                clinic.getId(),
                session.getId(),
                new com.cms.booking.StaffQueueBookingService.BookSlotInput(
                        patient.getId(), null, null, appointmentType.getId()));
    }

    private java.util.UUID accountIdFor(String token) {
        return staffJwtService.validateAndGetAccountId(token).orElseThrow();
    }
}
