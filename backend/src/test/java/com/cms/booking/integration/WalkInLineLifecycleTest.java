package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.domain.AppointmentType;
import com.cms.booking.domain.Booking;
import com.cms.booking.domain.BookingSource;
import com.cms.booking.domain.BookingStatus;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.inbox.repository.InboxItemRepository;
import com.cms.patient.account.domain.PatientAccount;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import com.cms.waitlist.domain.WaitlistEntry;
import com.cms.waitlist.domain.WaitlistEntryStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * 063-front-desk-walk-in (US2, FR-013-FR-015, tasks.md T026): working the walk-in line through the
 * real endpoints - "Send in" (existing Appeared) stamps the send-in time, "Complete" stamps the finish
 * time, and "Remove" (existing staff cancel) cancels the walk-in without making it a no-show,
 * without offering its untimed slot to the waitlist, and without the slot ever being listed.
 */
class WalkInLineLifecycleTest extends AbstractDeVerificationCascadeIntegrationTest {

    @Autowired
    private InboxItemRepository inboxItemRepository;

    @AfterEach
    void cleanInboxItems() {
        inboxItemRepository.deleteAll();
    }

    @Test
    void sendInCompleteAndRemoveWorkTheLineEndToEnd() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        AppointmentType type = appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", new BigDecimal("300.00")));
        Session any = saveFixedTimeSessionWithSlots(clinic, doctor);
        Session today = sessionRepository.findBySchedule_Id(any.getSchedule().getId()).stream()
                .filter(s -> s.getSessionDate().equals(LocalDate.now()))
                .findFirst()
                .orElseThrow();
        PatientAccount waiting = savePatientAccount();
        WaitlistEntry entry = saveWaitingEntry(clinic, doctor, waiting);
        String token = clinicAdminToken(clinic);

        for (String name : List.of("Asha Rao", "Ravi Kumar")) {
            mockMvc.perform(post("/api/v1/clinics/{clinicId}/walk-ins", clinic.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sessionId\":\"" + today.getId() + "\",\"patientName\":\"" + name
                                    + "\",\"appointmentTypeId\":\"" + type.getId() + "\",\"visitReason\":\"PAIN\"}"))
                    .andExpect(status().isCreated());
        }
        List<Booking> walkIns = bookingRepository.findAll().stream()
                .filter(b -> b.getSource() == BookingSource.WALK_IN)
                .sorted(Comparator.comparing(b -> b.getSlot().getTokenNumber()))
                .toList();
        Slot w1 = walkIns.get(0).getSlot();
        Booking w2 = walkIns.get(1);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/slots/{slotId}/appeared", clinic.getId(), w1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        Slot sentIn = slotRepository.findById(w1.getId()).orElseThrow();
        assertThat(sentIn.getStatus()).isEqualTo(SlotStatus.APPEARED);
        assertThat(sentIn.getAppearedAt()).isNotNull();

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/slots/{slotId}/complete", clinic.getId(), w1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        Slot completed = slotRepository.findById(w1.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(SlotStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isNotNull();

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/bookings/{bookingId}/cancel", clinic.getId(), w2.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(bookingRepository.findById(w2.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.CANCELLED);
        Slot removedSlot = slotRepository.findById(w2.getSlot().getId()).orElseThrow();
        assertThat(removedSlot.getStatus()).isNotEqualTo(SlotStatus.NO_SHOW);
        assertThat(waitlistEntryRepository.findById(entry.getId()).orElseThrow().getStatus())
                .isEqualTo(WaitlistEntryStatus.WAITING);
        assertThat(slotRepository.findOpenFixedTimeSlots(
                                clinic.getId(), doctor.getId(), LocalDate.now(), LocalTime.MIN, PageRequest.of(0, 200))
                        .getContent())
                .extracting(Slot::getId)
                .doesNotContain(removedSlot.getId());
    }
}
