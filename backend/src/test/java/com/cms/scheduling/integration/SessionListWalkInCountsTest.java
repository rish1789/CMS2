package com.cms.scheduling.integration;

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
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 063-front-desk-walk-in (research.md Decision 6, tasks.md T017): the session list gives the front
 * desk each Fixed-Time session's walk-ins waiting and the Doctor free/busy hint, from slot status
 * alone.
 *
 * <p>064-queue-send-in-complete (US3, FR-007, tasks.md T014): a Queue session now reports the same
 * two figures - every waiting token is in its line, and a token sent in makes the doctor "busy".
 */
class SessionListWalkInCountsTest extends AbstractSessionGenerationIntegrationTest {

    @Autowired
    private SlotRepository slotRepository;

    private ResultActions listToday(Clinic clinic) throws Exception {
        return mockMvc.perform(get("/api/v1/clinics/{clinicId}/sessions", clinic.getId())
                .param("from", LocalDate.now().toString())
                .param("to", LocalDate.now().toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)));
    }

    @Test
    void walkInsWaitingAndInWithDoctorFollowTheWalkInLine() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        var schedule = saveEveryDaySchedule(clinic, doctor, ScheduleMode.FIXED_TIME, 15);
        sessionGenerationService.generate(LocalDate.now());
        Session today = sessionRepository.findBySchedule_Id(schedule.getId()).stream()
                .filter(s -> s.getSessionDate().equals(LocalDate.now()))
                .findFirst()
                .orElseThrow();

        listToday(clinic)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[0].walkInsWaiting").value(0))
                .andExpect(jsonPath("$.sessions[0].inWithDoctor").value(false));

        Slot w1 = new Slot(today, 1);
        w1.setStatus(SlotStatus.BOOKED);
        slotRepository.save(w1);
        Slot w2 = new Slot(today, 2);
        w2.setStatus(SlotStatus.BOOKED);
        slotRepository.save(w2);

        listToday(clinic)
                .andExpect(jsonPath("$.sessions[0].walkInsWaiting").value(2))
                .andExpect(jsonPath("$.sessions[0].inWithDoctor").value(false));

        w1.setStatus(SlotStatus.APPEARED);
        slotRepository.save(w1);

        listToday(clinic)
                .andExpect(jsonPath("$.sessions[0].walkInsWaiting").value(1))
                .andExpect(jsonPath("$.sessions[0].inWithDoctor").value(true));
    }

    @Test
    void aQueueSessionReportsItsWaitingTokensAndWhoIsInWithTheDoctor() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        var schedule = saveEveryDaySchedule(clinic, doctor, ScheduleMode.QUEUE, null);
        sessionGenerationService.generate(LocalDate.now());
        Session today = sessionRepository.findBySchedule_Id(schedule.getId()).stream()
                .filter(s -> s.getSessionDate().equals(LocalDate.now()))
                .findFirst()
                .orElseThrow();

        listToday(clinic)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[0].mode").value("QUEUE"))
                .andExpect(jsonPath("$.sessions[0].walkInsWaiting").value(0))
                .andExpect(jsonPath("$.sessions[0].inWithDoctor").value(false));

        Slot t1 = new Slot(today, 1);
        t1.setStatus(SlotStatus.BOOKED);
        slotRepository.save(t1);
        Slot t2 = new Slot(today, 2);
        t2.setStatus(SlotStatus.BOOKED);
        slotRepository.save(t2);
        Slot cancelled = new Slot(today, 3); // a cancelled booking's freed token stays OPEN
        slotRepository.save(cancelled);

        listToday(clinic)
                .andExpect(jsonPath("$.sessions[0].walkInsWaiting").value(2))
                .andExpect(jsonPath("$.sessions[0].inWithDoctor").value(false));

        t1.setStatus(SlotStatus.APPEARED);
        slotRepository.save(t1);

        listToday(clinic)
                .andExpect(jsonPath("$.sessions[0].walkInsWaiting").value(1))
                .andExpect(jsonPath("$.sessions[0].inWithDoctor").value(true));
    }
}
