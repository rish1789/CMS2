package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.scheduling.Schedule;
import com.cms.scheduling.ScheduleMode;
import com.cms.scheduling.SessionGenerationService;
import com.cms.scheduling.Slot;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 024 FR-001..FR-008, spec US1/US2: the risk-based buffer slot calculator. */
class RiskBasedBufferSlotCalculatorTest extends AbstractBufferSlotCalculatorIntegrationTest {

    @Autowired
    private SessionGenerationService sessionGenerationService;

    @Test
    void zeroBookingHistoryGetsTheFlatDefaultOfOne() {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionOn(clinic, doctor, LocalDate.now());

        assertThat(riskBasedBufferSlotCalculator.calculateBufferSlotCount(session)).isEqualTo(1);
    }

    @Test
    void fewerThanFiveBookingsGetsTheFlatDefaultOfOne() {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        saveBookingHistory(clinic, doctor, LocalDate.now().minusDays(10), 4, 4); // all no-show, but below the 5-sample floor
        var session = saveFixedTimeSessionOn(clinic, doctor, LocalDate.now());

        assertThat(riskBasedBufferSlotCalculator.calculateBufferSlotCount(session)).isEqualTo(1);
    }

    @Test
    void fiveOrMoreBookingsOutsideTheNinetyDayWindowStillGetsTheFlatDefaultOfOne() {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionOn(clinic, doctor, LocalDate.now());
        saveBookingHistory(clinic, doctor, session.getSessionDate().minusDays(91), 8, 8); // outside the window

        assertThat(riskBasedBufferSlotCalculator.calculateBufferSlotCount(session)).isEqualTo(1);
    }

    @Test
    void fiveOrMoreBookingsWithANonzeroNoShowRateComputesADirectPercentage() {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        // 2 of 10 no-show = 20% rate -> exactly at the 20% cap boundary.
        saveBookingHistory(clinic, doctor, LocalDate.now().minusDays(10), 10, 2);
        // 9:00-13:00, 15-min = 16 slots; 20% of 16 = 3.2 -> rounds to 3, which also happens to equal the absolute cap.
        var session = saveFixedTimeSessionOn(clinic, doctor, LocalDate.now());

        assertThat(riskBasedBufferSlotCalculator.calculateBufferSlotCount(session)).isEqualTo(3);
    }

    @Test
    void fiveOrMoreBookingsWithZeroNoShowsComputesZeroNotTheFlatDefault() {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        saveBookingHistory(clinic, doctor, LocalDate.now().minusDays(10), 6, 0);
        var session = saveFixedTimeSessionOn(clinic, doctor, LocalDate.now());

        assertThat(riskBasedBufferSlotCalculator.calculateBufferSlotCount(session)).isEqualTo(0);
    }

    @Test
    void aVeryHighNoShowRateNeverExceedsTwentyPercentOfTheSessionsSlots() {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        saveBookingHistory(clinic, doctor, LocalDate.now().minusDays(10), 10, 10); // 100% no-show rate
        // A small session: 9:00-9:30, 15-min = 2 slots. Without the 20% cap, 100% of 2 = 2. With it, 20% of 2 = 0.4 -> 0.
        var session = saveFixedTimeSessionOn(clinic, doctor, LocalDate.now(), LocalTime.of(9, 0), LocalTime.of(9, 30), 15);

        assertThat(riskBasedBufferSlotCalculator.calculateBufferSlotCount(session)).isEqualTo(0);
    }

    @Test
    void aHighTwentyPercentFigureIsCappedAtThreeAbsolute() {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        saveBookingHistory(clinic, doctor, LocalDate.now().minusDays(10), 10, 10); // 100% no-show rate
        // A large session: 8:00-20:00, 15-min = 48 slots. 20% of 48 = 9.6 -> would round to 10 without the absolute cap.
        var session = saveFixedTimeSessionOn(clinic, doctor, LocalDate.now(), LocalTime.of(8, 0), LocalTime.of(20, 0), 15);

        assertThat(riskBasedBufferSlotCalculator.calculateBufferSlotCount(session)).isEqualTo(3);
    }

    @Test
    void howeverManyBufferSlotsAreComputedTheyAreStillDistributedEvenlyWhenGenerated() {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        saveBookingHistory(clinic, doctor, LocalDate.now().minusDays(10), 10, 2); // -> 3 buffer slots (see above)
        var schedule = scheduleRepository.save(new Schedule(
                doctor, clinic, EnumSet.allOf(DayOfWeek.class), LocalTime.of(9, 0), LocalTime.of(13, 0), ScheduleMode.FIXED_TIME, 15));

        sessionGenerationService.generate(LocalDate.now());

        var sessions = sessionRepository.findBySchedule_Id(schedule.getId());
        var slots = slotRepository.findBySession_Id(sessions.get(0).getId()).stream()
                .sorted(Comparator.comparing(Slot::getStartTime))
                .toList();
        long bufferCount = slots.stream().filter(Slot::isBuffer).count();

        assertThat(bufferCount).isEqualTo(3);
        // index(i) = floor((i + 0.5) * 16 / 3) for i=0,1,2 -> indices 2, 8, 13 (018's own proven formula).
        assertThat(slots.get(2).isBuffer()).isTrue();
        assertThat(slots.get(8).isBuffer()).isTrue();
        assertThat(slots.get(13).isBuffer()).isTrue();
    }
}
