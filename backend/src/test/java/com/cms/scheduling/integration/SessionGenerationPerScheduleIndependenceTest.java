package com.cms.scheduling.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.scheduling.ScheduleMode;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 015 FR-005, spec US1 AC3: two Schedules for two different doctors each get their own independently-correct Session set. */
class SessionGenerationPerScheduleIndependenceTest extends AbstractSessionGenerationIntegrationTest {

    @Test
    void twoSchedulesForDifferentDoctorsAreGeneratedIndependently() {
        var clinicA = saveClinic();
        var clinicB = saveClinic();
        var doctorA = saveDoctorStaffedAt(clinicA);
        var doctorB = saveDoctorStaffedAt(clinicB);
        var scheduleA = saveEveryDaySchedule(clinicA, doctorA, ScheduleMode.QUEUE, null);
        var scheduleB = saveEveryDaySchedule(clinicB, doctorB, ScheduleMode.QUEUE, null);
        LocalDate runDate = LocalDate.of(2026, 9, 3);

        int created = sessionGenerationService.generate(runDate);

        assertThat(created).isEqualTo(30);
        var sessionsA = sessionRepository.findBySchedule_Id(scheduleA.getId());
        var sessionsB = sessionRepository.findBySchedule_Id(scheduleB.getId());
        assertThat(sessionsA).hasSize(15);
        assertThat(sessionsB).hasSize(15);
        assertThat(sessionsA).allSatisfy(s -> assertThat(s.getDoctorProfile().getId()).isEqualTo(doctorA.getId()));
        assertThat(sessionsB).allSatisfy(s -> assertThat(s.getDoctorProfile().getId()).isEqualTo(doctorB.getId()));
    }
}
