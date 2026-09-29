package com.cms.scheduling.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.scheduling.domain.ScheduleMode;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import com.cms.scheduling.repository.SlotRepository;
import com.cms.scheduling.service.NoShowDetectionService;
import com.cms.scheduling.service.SlotAutoCompletionService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;

/**
 * 063-front-desk-walk-in (research.md Decision 3, tasks.md T007): an untimed walk-in slot inside a
 * Fixed-Time session is invisible to every sweep and listing that assumes a scheduled time - the
 * no-show sweep, the auto-completion sweep, the patient open-slot listings - and doesn't inflate the
 * session's timed slot counts.
 */
class UntimedSlotSweepsTest extends AbstractSessionGenerationIntegrationTest {

    @Autowired
    private SlotRepository slotRepository;

    @Autowired
    private NoShowDetectionService noShowDetectionService;

    @Autowired
    private SlotAutoCompletionService slotAutoCompletionService;

    private Session sessionOn(UUID scheduleId, LocalDate date) {
        return sessionRepository.findBySchedule_Id(scheduleId).stream()
                .filter(s -> s.getSessionDate().equals(date))
                .findFirst()
                .orElseThrow();
    }

    private Slot walkIn(Session session, int token, SlotStatus status) {
        Slot slot = new Slot(session, token);
        slot.setStatus(status);
        return slotRepository.save(slot);
    }

    @Test
    void theNoShowAndAutoCompletionSweepsNeverTouchAnUntimedWalkIn() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        var schedule = saveEveryDaySchedule(clinic, doctor, ScheduleMode.FIXED_TIME, 15);
        LocalDate yesterday = LocalDate.now().minusDays(1);
        sessionGenerationService.generate(yesterday);
        Session past = sessionOn(schedule.getId(), yesterday);
        Slot waiting = walkIn(past, 1, SlotStatus.BOOKED);
        Slot inWithDoctor = walkIn(past, 2, SlotStatus.APPEARED);

        noShowDetectionService.detectAndMarkNoShows();
        slotAutoCompletionService.completeExpiredAppearedSlots();

        assertThat(slotRepository.findById(waiting.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.BOOKED);
        assertThat(slotRepository.findById(inWithDoctor.getId()).orElseThrow().getStatus())
                .isEqualTo(SlotStatus.APPEARED);
    }

    @Test
    void anOpenUntimedSlotIsNeverOfferedForBookingAndDoesNotInflateTimedCounts() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        var schedule = saveEveryDaySchedule(clinic, doctor, ScheduleMode.FIXED_TIME, 15); // 09:00-10:00 = 4 slots
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        sessionGenerationService.generate(LocalDate.now());
        Session session = sessionOn(schedule.getId(), tomorrow);
        Slot removedWalkIn = walkIn(session, 1, SlotStatus.OPEN);
        walkIn(session, 2, SlotStatus.BOOKED);

        var listed = slotRepository.findOpenFixedTimeSlots(
                clinic.getId(), doctor.getId(), LocalDate.now(), LocalTime.MIN, PageRequest.of(0, 100));
        var listedOnDate = slotRepository.findOpenFixedTimeSlotsOnDate(
                clinic.getId(), doctor.getId(), tomorrow, LocalDate.now(), LocalTime.MIN, PageRequest.of(0, 100));

        assertThat(listed.getContent()).extracting(Slot::getId).doesNotContain(removedWalkIn.getId());
        assertThat(listedOnDate.getContent()).extracting(Slot::getId).doesNotContain(removedWalkIn.getId());
        assertThat(listedOnDate.getTotalElements()).isEqualTo(4);

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/sessions", clinic.getId())
                        .param("from", tomorrow.toString())
                        .param("to", tomorrow.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[0].totalSlotCount").value(4))
                .andExpect(jsonPath("$.sessions[0].bookedSlotCount").value(0));
    }
}
