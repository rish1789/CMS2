package com.cms.booking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.Booking;
import com.cms.identity.account.RoleAssignment;
import com.cms.scheduling.SlotStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

/** 027 US1 (P1), FR-006/FR-007/SC-003: not-applicable cases never produce a numeric value. */
class QueuePositionNotApplicableTest extends AbstractQueuePositionIntegrationTest {

    private ResultActions queuePosition(String clinicId, String bookingId, String token) throws Exception {
        return mockMvc.perform(get("/api/v1/clinics/{clinicId}/bookings/{bookingId}/queue-position", clinicId, bookingId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    void fixedTimeSessionsBookingIsNotApplicable() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSession(clinic, doctor);
        var slot = slotRepository.findBySession_Id(session.getId()).get(0);
        var patient = saveExistingPatient(clinic);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        var counterAdmin = accountRepository.save(new com.cms.identity.account.Account(
                "Admin FT", "adminft-" + java.util.UUID.randomUUID() + "@example.com",
                passwordEncoder.encode("Str0ng!Pass"), "CAFT-" + java.util.UUID.randomUUID(), null));
        roleAssignmentRepository.save(new RoleAssignment(counterAdmin, clinic, RoleAssignment.Role.ClinicAdmin));
        String token = staffJwtService.issueToken(counterAdmin.getId());

        Booking booking = bookingRepository.save(
                new Booking(slot, patient, appointmentType, new BigDecimal("300.00"), counterAdmin.getId()));

        queuePosition(clinic.getId().toString(), booking.getId().toString(), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicable").value(false))
                .andExpect(jsonPath("$.position").doesNotExist());
    }

    @Test
    void ownBookingAlreadyCompletedIsNotApplicable() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveQueueSession(clinic, doctor);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        var patient = saveExistingPatient(clinic);
        String token = clinicAdminToken(clinic);
        var callerAccountId = staffJwtService.validateAndGetAccountId(token).orElseThrow();

        Booking booking = staffQueueBookingService.bookSlot(
                callerAccountId, clinic.getId(), session.getId(),
                new com.cms.booking.StaffQueueBookingService.BookSlotInput(patient.getId(), null, null, appointmentType.getId()));
        setSlotStatus(booking, SlotStatus.COMPLETED);

        queuePosition(clinic.getId().toString(), booking.getId().toString(), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicable").value(false))
                .andExpect(jsonPath("$.position").doesNotExist());
    }

    @Test
    void ownBookingAlreadyNoShowIsNotApplicable() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveQueueSession(clinic, doctor);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        var patient = saveExistingPatient(clinic);
        String token = clinicAdminToken(clinic);
        var callerAccountId = staffJwtService.validateAndGetAccountId(token).orElseThrow();

        Booking booking = staffQueueBookingService.bookSlot(
                callerAccountId, clinic.getId(), session.getId(),
                new com.cms.booking.StaffQueueBookingService.BookSlotInput(patient.getId(), null, null, appointmentType.getId()));
        setSlotStatus(booking, SlotStatus.NO_SHOW);

        queuePosition(clinic.getId().toString(), booking.getId().toString(), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicable").value(false));
    }
}
