package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.domain.AppointmentType;
import com.cms.booking.domain.Booking;
import com.cms.booking.domain.BookingStatus;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.inbox.repository.InboxItemRepository;
import com.cms.patient.account.config.JwtService;
import com.cms.patient.account.domain.PatientAccount;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import com.cms.waitlist.domain.WaitlistEntry;
import com.cms.waitlist.domain.WaitlistEntryStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * 064-queue-send-in-complete (US1, FR-001-FR-003, FR-009, tasks.md T010): through the real endpoints,
 * every queue booking path mints a waiting token; staff send a token in and complete it, with both
 * times recorded; and staff cancelling a waiting queue booking frees its token without a waitlist offer.
 */
class QueueSendInCompleteTest extends AbstractDeVerificationCascadeIntegrationTest {

    @Autowired
    private JwtService patientJwtService;

    @Autowired
    private InboxItemRepository inboxItemRepository;

    @AfterEach
    void cleanInboxItems() {
        inboxItemRepository.deleteAll();
    }

    @Test
    void queueTokensAreWaitingAndCanBeSentInCompletedAndCancelled() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        AppointmentType type = clinicPriceFixtures.priceAtStaffedClinics(
                appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", null)), new BigDecimal("300.00"));
        Session any = saveQueueSession(clinic, doctor);
        Session today = sessionRepository.findBySchedule_Id(any.getSchedule().getId()).stream()
                .filter(s -> s.getSessionDate().equals(LocalDate.now()))
                .findFirst()
                .orElseThrow();
        PatientAccount patient = savePatientAccount();
        WaitlistEntry waiting = saveWaitingEntry(clinic, doctor, savePatientAccount());
        String staffToken = clinicAdminToken(clinic);
        String body = "{\"patientName\":\"%s\",\"appointmentTypeId\":\"" + type.getId() + "\"}";

        mockMvc.perform(post("/api/v1/patients/clinics/{clinicId}/sessions/{sessionId}/queue-bookings", clinic.getId(), today.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientJwtService.issueToken(patient.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(body, "Patient Booked")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/clinics/{clinicId}/sessions/{sessionId}/queue-bookings", clinic.getId(), today.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(body, "Staff Booked")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/clinics/{clinicId}/walk-ins", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"" + today.getId() + "\",\"patientName\":\"Front Desk\",\"appointmentTypeId\":\""
                                + type.getId() + "\",\"visitReason\":\"PAIN\"}"))
                .andExpect(status().isCreated());

        List<Slot> tokens = slotRepository.findBySession_Id(today.getId()).stream()
                .sorted(Comparator.comparing(Slot::getTokenNumber))
                .toList();
        assertThat(tokens).hasSize(3).allSatisfy(t -> assertThat(t.getStatus()).isEqualTo(SlotStatus.BOOKED));

        Slot first = tokens.get(0);
        mockMvc.perform(post("/api/v1/clinics/{clinicId}/slots/{slotId}/appeared", clinic.getId(), first.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffToken))
                .andExpect(status().isOk());
        assertThat(slotRepository.findById(first.getId()).orElseThrow().getAppearedAt()).isNotNull();

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/slots/{slotId}/complete", clinic.getId(), first.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffToken))
                .andExpect(status().isOk());
        Slot completed = slotRepository.findById(first.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(SlotStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isNotNull();

        Booking second = bookingRepository.findAll().stream()
                .filter(b -> b.getSlot().getId().equals(tokens.get(1).getId()))
                .findFirst()
                .orElseThrow();
        mockMvc.perform(post("/api/v1/clinics/{clinicId}/bookings/{bookingId}/cancel", clinic.getId(), second.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffToken))
                .andExpect(status().isOk());
        assertThat(bookingRepository.findById(second.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(slotRepository.findById(tokens.get(1).getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.OPEN);
        assertThat(waitlistEntryRepository.findById(waiting.getId()).orElseThrow().getStatus())
                .isEqualTo(WaitlistEntryStatus.WAITING);
    }
}
