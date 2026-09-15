package com.cms.scheduling.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 015 FR-002, spec US1 AC2: calling generate() twice in a row creates zero duplicates. */
class SessionGenerationNoDuplicateOnRepeatTest extends AbstractSessionGenerationIntegrationTest {

    @Test
    void repeatedGenerationCreatesNoDuplicates() {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var schedule = saveFixedTimeSchedule(clinic, doctor);
        LocalDate runDate = LocalDate.of(2026, 9, 3);

        int firstRun = sessionGenerationService.generate(runDate);
        assertThat(firstRun).isGreaterThan(0);

        int secondRun = sessionGenerationService.generate(runDate);
        assertThat(secondRun).isZero();

        assertThat(sessionRepository.findBySchedule_Id(schedule.getId())).hasSize(firstRun);
    }

    @Test
    void repeatedGenerationOnALaterDateOnlyAddsNewlyInRangeDates() {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var schedule = saveEveryDaySchedule(clinic, doctor, com.cms.scheduling.ScheduleMode.QUEUE, null);
        LocalDate runDate = LocalDate.of(2026, 9, 3);

        int firstRun = sessionGenerationService.generate(runDate);
        assertThat(firstRun).isEqualTo(15);

        // Running again one day later extends the horizon by exactly one new date.
        int secondRun = sessionGenerationService.generate(runDate.plusDays(1));
        assertThat(secondRun).isEqualTo(1);

        assertThat(sessionRepository.findBySchedule_Id(schedule.getId())).hasSize(16);
    }
}
