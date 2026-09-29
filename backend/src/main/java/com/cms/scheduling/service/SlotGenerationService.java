package com.cms.scheduling.service;

import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.repository.SlotRepository;


import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 018: pre-generates every Slot of a Fixed-Time {@link Session} in one call, invoked by
 * {@link ScheduleSessionGenerator#generateForSchedule} in the same transaction as the
 * Session's own creation (FR-001). Never called for Queue/Token Sessions (FR-003, that
 * caller's own responsibility to gate).
 */
@Service
public class SlotGenerationService {

    private final SlotRepository slotRepository;

    public SlotGenerationService(SlotRepository slotRepository) {
        this.slotRepository = slotRepository;
    }

    @Transactional
    public List<Slot> generateSlotsFor(Session session) {
        List<LocalTime> slotStartTimes = computeSlotStartTimes(session);

        List<Slot> slots = new ArrayList<>();
        for (LocalTime start : slotStartTimes) {
            LocalTime end = start.plusMinutes(session.getSlotIntervalMinutes());
            slots.add(new Slot(session, start, end));
        }
        return slotRepository.saveAll(slots);
    }

    /**
     * FR-004: never a trailing partial interval - only start times where start+interval fits
     * within the Session's own end time. 055-schedule-break-window: any candidate slot whose
     * own [cursor, cursor+interval) range overlaps [breakStartTime, breakEndTime) at all -
     * including one that starts before the break but would run into it - is skipped, jumping
     * the cursor straight to breakEndTime instead.
     *
     * <p>Arithmetic is done in plain minutes-of-day (not {@link LocalTime#plusMinutes}) because
     * {@code LocalTime} wraps modulo 24h: a schedule whose last slot boundary lands exactly on
     * midnight (e.g. 00:00-23:45 in 15-minute steps) would otherwise wrap to 00:00, which
     * {@code isAfter(endTime)} never sees as past the end - producing an infinite loop and an
     * OutOfMemoryError instead of terminating.
     */
    private List<LocalTime> computeSlotStartTimes(Session session) {
        List<LocalTime> starts = new ArrayList<>();
        int intervalMinutes = session.getSlotIntervalMinutes();
        int cursorMinute = session.getStartTime().toSecondOfDay() / 60;
        int endMinute = session.getEndTime().toSecondOfDay() / 60;
        LocalTime breakStart = session.getBreakStartTime();
        Integer breakStartMinute = breakStart != null ? breakStart.toSecondOfDay() / 60 : null;
        Integer breakEndMinute =
                session.getBreakEndTime() != null ? session.getBreakEndTime().toSecondOfDay() / 60 : null;
        while (cursorMinute + intervalMinutes <= endMinute) {
            if (breakStartMinute != null
                    && cursorMinute < breakEndMinute
                    && cursorMinute + intervalMinutes > breakStartMinute) {
                cursorMinute = breakEndMinute;
                continue;
            }
            starts.add(LocalTime.ofSecondOfDay(cursorMinute * 60L));
            cursorMinute += intervalMinutes;
        }
        return starts;
    }
}
