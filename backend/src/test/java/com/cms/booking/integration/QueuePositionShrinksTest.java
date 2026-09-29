package com.cms.booking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.domain.AppointmentType;
import com.cms.booking.domain.Booking;
import com.cms.booking.service.PatientQueueBookingService;
import com.cms.booking.service.StaffQueueBookingService;
import com.cms.identity.clinic.Clinic;
import com.cms.patient.account.domain.PatientAccount;
import com.cms.scheduling.domain.Session;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * 064-queue-send-in-complete (US2, FR-004/FR-005/SC-002, tasks.md T013): through the real staff and
 * patient queue-position endpoints, a patient's position falls as staff send the tokens ahead in or
 * cancel them, and stops being applicable once the patient themselves is sent in.
 */
class QueuePositionShrinksTest extends AbstractQueuePositionIntegrationTest {

    @Test
    void positionFallsAsTokensAheadAreSentInOrCancelled() throws Exception {
        Clinic clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Session session = saveQueueSession(clinic, doctor);
        AppointmentType type = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        String staff = clinicAdminToken(clinic);
        UUID staffId = staffJwtService.validateAndGetAccountId(staff).orElseThrow();

        Booking first = staffBook(clinic, session, type, staffId);
        Booking second = staffBook(clinic, session, type, staffId);
        staffBook(clinic, session, type, staffId);
        PatientAccount account = savePatientAccount();
        Booking mine = patientQueueBookingService.bookSlot(
                account.getId(), clinic.getId(), session.getId(), new PatientQueueBookingService.BookSlotInput("Queue Patient", type.getId()));
        String patient = patientToken(account);

        expectPatientPosition(mine, patient, 4);
        expectStaffPosition(clinic, mine, staff, 4);

        staffAction(clinic, "/slots/" + first.getSlot().getId() + "/appeared", staff);
        expectPatientPosition(mine, patient, 3);

        staffAction(clinic, "/bookings/" + second.getId() + "/cancel", staff);
        expectPatientPosition(mine, patient, 2);
        expectStaffPosition(clinic, mine, staff, 2);

        staffAction(clinic, "/slots/" + mine.getSlot().getId() + "/appeared", staff);
        mockMvc.perform(get("/api/v1/patients/bookings/{bookingId}/queue-position", mine.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicable").value(false));
    }

    private Booking staffBook(Clinic clinic, Session session, AppointmentType type, UUID staffId) {
        var patient = saveExistingPatient(clinic);
        return staffQueueBookingService.bookSlot(
                staffId, clinic.getId(), session.getId(),
                new StaffQueueBookingService.BookSlotInput(patient.getId(), null, null, type.getId()));
    }

    private void staffAction(Clinic clinic, String path, String staff) throws Exception {
        mockMvc.perform(post("/api/v1/clinics/" + clinic.getId() + path).header(HttpHeaders.AUTHORIZATION, "Bearer " + staff))
                .andExpect(status().isOk());
    }

    private void expectPatientPosition(Booking booking, String patient, int position) throws Exception {
        mockMvc.perform(get("/api/v1/patients/bookings/{bookingId}/queue-position", booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patient))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicable").value(true))
                .andExpect(jsonPath("$.position").value(position));
    }

    private void expectStaffPosition(Clinic clinic, Booking booking, String staff, int position) throws Exception {
        mockMvc.perform(get("/api/v1/clinics/{clinicId}/bookings/{bookingId}/queue-position", clinic.getId(), booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(position));
    }
}
