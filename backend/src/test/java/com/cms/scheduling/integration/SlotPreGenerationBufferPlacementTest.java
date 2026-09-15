package com.cms.scheduling.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.scheduling.ScheduleMode;
import com.cms.scheduling.Slot;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 018 FR-005/FR-006, spec US2 AC1-AC2: exactly 1 buffer Slot, placed at the middle index. */
class SlotPreGenerationBufferPlacementTest extends AbstractSlotGenerationIntegrationTest {

    @Test
    void sixteenSlotSessionHasExactlyOneBufferSlotAtTheMiddleIndex() {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var schedule = saveFixedTimeSchedule(clinic, doctor); // 9:00-13:00, 15-min -> 16 slots
        LocalDate runDate = LocalDate.of(2026, 9, 3);

        sessionGenerationService.generate(runDate);

        var sessions = sessionRepository.findBySchedule_Id(schedule.getId());
        List<Slot> slots = slotRepository.findBySession_Id(sessions.get(0).getId()).stream()
                .sorted(Comparator.comparing(Slot::getStartTime))
                .toList();

        long bufferCount = slots.stream().filter(Slot::isBuffer).count();
        assertThat(bufferCount).isEqualTo(1);
        assertThat(slots.get(8).isBuffer()).isTrue(); // floor(0.5 * 16 / 1) = 8
    }

    @Test
    void singleSlotSessionsSoleSlotIsItselfTheBufferSlot() {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var schedule = scheduleRepository.save(new com.cms.scheduling.Schedule(
                doctor, clinic, EnumSet.allOf(DayOfWeek.class),
                LocalTime.of(9, 0), LocalTime.of(9, 15), ScheduleMode.FIXED_TIME, 15));
        LocalDate runDate = LocalDate.of(2026, 9, 3);

        sessionGenerationService.generate(runDate);

        var sessions = sessionRepository.findBySchedule_Id(schedule.getId());
        var slots = slotRepository.findBySession_Id(sessions.get(0).getId());

        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).isBuffer()).isTrue();
    }
}
