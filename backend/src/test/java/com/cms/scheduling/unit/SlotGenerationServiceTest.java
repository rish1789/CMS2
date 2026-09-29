package com.cms.scheduling.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.scheduling.domain.ScheduleMode;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.repository.SlotRepository;
import com.cms.scheduling.service.SlotGenerationService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 055-schedule-break-window: covers {@code computeSlotStartTimes}'s break-skipping rule -
 * a single merged Schedule (e.g. 9:00-18:00 with a 14:00-16:00 lunch break) must never
 * generate a Slot inside, or running into, its own break window. Pure Mockito - no Spring
 * context, no Docker.
 */
@ExtendWith(MockitoExtension.class)
class SlotGenerationServiceTest {

    @Mock
    private SlotRepository slotRepository;

    @Mock
    private Clinic clinic;

    @Mock
    private DoctorProfile doctorProfile;

    private SlotGenerationService newService() {
        return new SlotGenerationService(slotRepository);
    }

    private Session session(LocalTime start, LocalTime end, int intervalMinutes, LocalTime breakStart, LocalTime breakEnd) {
        Session session = new Session(
                null, clinic, doctorProfile, LocalDate.of(2026, 9, 21), ScheduleMode.FIXED_TIME, start, end, intervalMinutes);
        session.setBreakStartTime(breakStart);
        session.setBreakEndTime(breakEnd);
        return session;
    }

    @SuppressWarnings("unchecked")
    private List<Slot> generate(Session session) {
        when(slotRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return newService().generateSlotsFor(session);
    }

    @Test
    void noBreakWindowPacksSlotsAcrossTheFullRange() {
        Session session = session(LocalTime.of(9, 0), LocalTime.of(10, 0), 30, null, null);

        List<Slot> slots = generate(session);

        assertThat(slots).extracting(Slot::getStartTime).containsExactly(LocalTime.of(9, 0), LocalTime.of(9, 30));
    }

    @Test
    void noSlotStartsInsideTheBreakWindow() {
        // 9:00-18:00, 30-minute slots, break 14:00-16:00 - the merged-schedule scenario this feature exists for.
        Session session = session(LocalTime.of(9, 0), LocalTime.of(18, 0), 30, LocalTime.of(14, 0), LocalTime.of(16, 0));

        List<Slot> slots = generate(session);

        assertThat(slots)
                .noneMatch(slot -> !slot.getStartTime().isBefore(LocalTime.of(14, 0))
                        && slot.getStartTime().isBefore(LocalTime.of(16, 0)));
        assertThat(slots).extracting(Slot::getStartTime).contains(LocalTime.of(13, 30), LocalTime.of(16, 0));
    }

    @Test
    void aSlotThatWouldRunIntoTheBreakIsSkippedEntirelyRatherThanTruncated() {
        // 45-minute slots starting 9:00 land on 13:45 next, which would run to 14:30 - into
        // the 14:00-16:00 break. That candidate is skipped outright, not shortened.
        Session session = session(LocalTime.of(9, 0), LocalTime.of(18, 0), 45, LocalTime.of(14, 0), LocalTime.of(16, 0));

        List<Slot> slots = generate(session);

        assertThat(slots).extracting(Slot::getStartTime).doesNotContain(LocalTime.of(13, 45));
        assertThat(slots)
                .noneMatch(slot -> slot.getStartTime().isBefore(LocalTime.of(14, 0))
                        && slot.getEndTime().isAfter(LocalTime.of(14, 0)));
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void aScheduleWhoseLastSlotBoundaryLandsExactlyOnMidnightTerminates() {
        // Regression: 23:30-23:45 in 15-minute steps makes the final cursor+interval land
        // exactly on 24:00, which LocalTime wraps to 00:00 - isAfter(23:45) then sees that
        // wrapped value as NOT past the end time, so a naive LocalTime-arithmetic loop never
        // terminates (this is what produced a real OutOfMemoryError in production).
        Session session = session(LocalTime.of(23, 30), LocalTime.of(23, 45), 15, null, null);

        List<Slot> slots = generate(session);

        assertThat(slots).extracting(Slot::getStartTime).containsExactly(LocalTime.of(23, 30));
    }
}
