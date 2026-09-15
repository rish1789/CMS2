package com.cms.scheduling.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.scheduling.SlotStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/** 018 FR-001/FR-002, spec US1 AC1: a Fixed-Time Session's Slots are all created immediately, correctly spaced, OPEN. */
class SlotPreGenerationFixedTimeTest extends AbstractSlotGenerationIntegrationTest {

    @Test
    void fixedTimeSessionGetsCorrectlySpacedOpenSlots() {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var schedule = saveFixedTimeSchedule(clinic, doctor); // 9:00-13:00, 15-min
        LocalDate runDate = LocalDate.of(2026, 9, 3);

        sessionGenerationService.generate(runDate);

        var sessions = sessionRepository.findBySchedule_Id(schedule.getId());
        assertThat(sessions).isNotEmpty();
        var slots = slotRepository.findBySession_Id(sessions.get(0).getId());

        assertThat(slots).hasSize(16);
        assertThat(slots).allSatisfy(s -> assertThat(s.getStatus()).isEqualTo(SlotStatus.OPEN));
        var sortedStarts = slots.stream().map(s -> s.getStartTime()).sorted().toList();
        assertThat(sortedStarts.get(0)).isEqualTo(LocalTime.of(9, 0));
        assertThat(sortedStarts.get(15)).isEqualTo(LocalTime.of(12, 45));
        for (int i = 0; i < sortedStarts.size(); i++) {
            assertThat(sortedStarts.get(i)).isEqualTo(LocalTime.of(9, 0).plusMinutes(15L * i));
        }
    }
}
