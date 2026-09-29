package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.domain.AppointmentType;
import com.cms.booking.domain.Booking;
import com.cms.booking.domain.BookingSource;
import com.cms.booking.domain.BookingStatus;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.inbox.domain.InboxItemType;
import com.cms.inbox.repository.InboxItemRepository;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 063-front-desk-walk-in (US1, SC-003, tasks.md T016): through the real endpoint and database - a
 * Fixed-Time walk-in joins the untimed walk-in line (W1, W2 with positions 1 and 2) without changing
 * any timed slot or booked appointment; a Queue walk-in takes the next token and is marked as a
 * walk-in; both raise the inbox walk-in item; a duplicate needs confirming.
 */
class FrontDeskWalkInRegistrationTest extends AbstractDeVerificationCascadeIntegrationTest {

    @Autowired
    private InboxItemRepository inboxItemRepository;

    /** Runs before the superclass cleanup: inbox_item references clinic without a cascade. */
    @AfterEach
    void cleanInboxItems() {
        inboxItemRepository.deleteAll();
    }

    private Session today(Session anySessionOfSchedule) {
        return sessionRepository.findBySchedule_Id(anySessionOfSchedule.getSchedule().getId()).stream()
                .filter(s -> s.getSessionDate().equals(LocalDate.now()))
                .findFirst()
                .orElseThrow();
    }

    private ResultActions register(Clinic clinic, String token, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/walk-ins", clinic.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String newPatient(UUID sessionId, AppointmentType type, String name, String email) {
        return "{\"sessionId\":\"" + sessionId + "\",\"patientName\":\"" + name + "\",\"patientEmail\":"
                + (email == null ? "null" : "\"" + email + "\"") + ",\"appointmentTypeId\":\"" + type.getId()
                + "\",\"visitReason\":\"PAIN\",\"confirmDuplicate\":false}";
    }

    @Test
    void fixedTimeWalkInsJoinTheWalkInLineWithoutTouchingTimedSlots() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        AppointmentType type = appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", new BigDecimal("300.00")));
        Session session = today(saveFixedTimeSessionWithSlots(clinic, doctor));
        List<Slot> timedBefore = slotRepository.findBySession_Id(session.getId()).stream()
                .sorted(Comparator.comparing(Slot::getStartTime))
                .toList();
        Booking booked = bookSlot(clinic, doctor, timedBefore.get(0));
        String token = clinicAdminToken(clinic);

        register(clinic, token, newPatient(session.getId(), type, "Asha Rao", "asha@example.com"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenNumber").value(1))
                .andExpect(jsonPath("$.walkInPosition").value(1));
        register(clinic, token, newPatient(session.getId(), type, "Ravi Kumar", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenNumber").value(2))
                .andExpect(jsonPath("$.walkInPosition").value(2));

        // SC-003: every timed slot and the booked appointment are exactly as they were.
        List<Slot> timedAfter = slotRepository.findBySession_Id(session.getId()).stream()
                .filter(s -> !s.isUntimed())
                .sorted(Comparator.comparing(Slot::getStartTime))
                .toList();
        assertThat(timedAfter).extracting(Slot::getStatus).containsExactlyElementsOf(
                timedBefore.stream().map(s -> s.getId().equals(booked.getSlot().getId()) ? SlotStatus.BOOKED : s.getStatus()).toList());
        assertThat(bookingRepository.findById(booked.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.ACTIVE);

        List<Booking> walkIns = bookingRepository.findAll().stream()
                .filter(b -> b.getSource() == BookingSource.WALK_IN)
                .toList();
        assertThat(walkIns).hasSize(2).allSatisfy(b -> assertThat(b.getSlot().isUntimed()).isTrue());
        assertThat(inboxItemRepository.findAll()).filteredOn(i -> i.getItemType() == InboxItemType.WALK_IN).hasSize(2);
    }

    @Test
    void aQueueWalkInTakesTheNextTokenAndADuplicateNeedsConfirming() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        AppointmentType type = appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", new BigDecimal("300.00")));
        Session queue = today(saveQueueSession(clinic, doctor));
        bookSlot(clinic, doctor, addQueueSlot(queue, 1));
        String token = clinicAdminToken(clinic);

        register(clinic, token, newPatient(queue.getId(), type, "Asha Rao", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value("QUEUE"))
                .andExpect(jsonPath("$.tokenNumber").value(2))
                .andExpect(jsonPath("$.walkInPosition").doesNotExist());

        UUID patientId = bookingRepository.findAll().stream()
                .filter(b -> b.getSource() == BookingSource.WALK_IN)
                .findFirst()
                .orElseThrow()
                .getPatient()
                .getId();
        String existing = "{\"sessionId\":\"" + queue.getId() + "\",\"patientId\":\"" + patientId
                + "\",\"appointmentTypeId\":\"" + type.getId() + "\",\"visitReason\":\"FOLLOW_UP\",\"confirmDuplicate\":%s}";

        register(clinic, token, String.format(existing, "false"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_WALK_IN"));
        register(clinic, token, String.format(existing, "true")).andExpect(status().isCreated());
    }
}
