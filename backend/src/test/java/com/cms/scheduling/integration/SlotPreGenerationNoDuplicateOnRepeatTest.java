package com.cms.scheduling.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.scheduling.ScheduleMode;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 018 FR-004, spec Edge Cases/US1 AC3: no trailing partial Slot; repeated generation never duplicates Slots. */
class SlotPreGenerationNoDuplicateOnRepeatTest extends AbstractSlotGenerationIntegrationTest {

    @Test
    void windowNotDivisibleByIntervalNeverProducesAPartialTrailingSlot() {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        // 50-minute window, 15-minute slots: 3 full slots fit (45 min); a 4th would overrun by 5 min.
        var schedule = scheduleRepository.save(new com.cms.scheduling.Schedule(
                doctor, clinic, java.util.EnumSet.allOf(java.time.DayOfWeek.class),
                java.time.LocalTime.of(9, 0), java.time.LocalTime.of(9, 50), ScheduleMode.FIXED_TIME, 15));
        LocalDate runDate = LocalDate.of(2026, 9, 3);

        sessionGenerationService.generate(runDate);

        var sessions = sessionRepository.findBySchedule_Id(schedule.getId());
        var slots = slotRepository.findBySession_Id(sessions.get(0).getId());
        assertThat(slots).hasSize(3);
        assertThat(slots).allSatisfy(
                s -> assertThat(s.getEndTime()).isBeforeOrEqualTo(java.time.LocalTime.of(9, 50)));
    }

    @Test
    void repeatedGenerationCreatesNoAdditionalSlotsForAnAlreadyGeneratedSession() {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var schedule = saveFixedTimeSchedule(clinic, doctor);
        LocalDate runDate = LocalDate.of(2026, 9, 3);

        sessionGenerationService.generate(runDate);
        var sessions = sessionRepository.findBySchedule_Id(schedule.getId());
        int slotCountBefore = slotRepository.findBySession_Id(sessions.get(0).getId()).size();

        sessionGenerationService.generate(runDate);
        int slotCountAfter = slotRepository.findBySession_Id(sessions.get(0).getId()).size();

        assertThat(slotCountAfter).isEqualTo(slotCountBefore);
    }
}
