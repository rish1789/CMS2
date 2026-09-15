package com.cms.scheduling.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.scheduling.ScheduleMode;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 015 FR-003/FR-004, spec US1 AC4-AC5: Fixed-Time Sessions carry the slot interval; Queue/Token Sessions carry none. */
class SessionGenerationModeSnapshotTest extends AbstractSessionGenerationIntegrationTest {

    @Test
    void fixedTimeSessionsCarryTheSlotInterval() {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var schedule = saveFixedTimeSchedule(clinic, doctor);
        LocalDate runDate = LocalDate.of(2026, 9, 3);

        sessionGenerationService.generate(runDate);

        var sessions = sessionRepository.findBySchedule_Id(schedule.getId());
        assertThat(sessions).isNotEmpty();
        assertThat(sessions).allSatisfy(s -> {
            assertThat(s.getMode()).isEqualTo(ScheduleMode.FIXED_TIME);
            assertThat(s.getSlotIntervalMinutes()).isEqualTo(15);
        });
    }

    @Test
    void queueSessionsCarryNoSlotInterval() {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var schedule = saveQueueSchedule(clinic, doctor);
        LocalDate runDate = LocalDate.of(2026, 9, 3);

        sessionGenerationService.generate(runDate);

        var sessions = sessionRepository.findBySchedule_Id(schedule.getId());
        assertThat(sessions).isNotEmpty();
        assertThat(sessions).allSatisfy(s -> {
            assertThat(s.getMode()).isEqualTo(ScheduleMode.QUEUE);
            assertThat(s.getSlotIntervalMinutes()).isNull();
        });
    }
}
