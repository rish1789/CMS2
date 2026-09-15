package com.cms.scheduling;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private final BufferSlotCalculator bufferSlotCalculator;

    public SlotGenerationService(SlotRepository slotRepository, BufferSlotCalculator bufferSlotCalculator) {
        this.slotRepository = slotRepository;
        this.bufferSlotCalculator = bufferSlotCalculator;
    }

    @Transactional
    public List<Slot> generateSlotsFor(Session session) {
        List<LocalTime> slotStartTimes = computeSlotStartTimes(session);
        int bufferCount = bufferSlotCalculator.calculateBufferSlotCount(session);
        Set<Integer> bufferIndices = computeEvenlySpacedIndices(slotStartTimes.size(), bufferCount);

        List<Slot> slots = new ArrayList<>();
        for (int i = 0; i < slotStartTimes.size(); i++) {
            LocalTime start = slotStartTimes.get(i);
            LocalTime end = start.plusMinutes(session.getSlotIntervalMinutes());
            slots.add(new Slot(session, start, end, bufferIndices.contains(i)));
        }
        return slotRepository.saveAll(slots);
    }

    /** FR-004: never a trailing partial interval - only start times where start+interval fits within the Session's own end time. */
    private List<LocalTime> computeSlotStartTimes(Session session) {
        List<LocalTime> starts = new ArrayList<>();
        LocalTime cursor = session.getStartTime();
        int intervalMinutes = session.getSlotIntervalMinutes();
        while (!cursor.plusMinutes(intervalMinutes).isAfter(session.getEndTime())) {
            starts.add(cursor);
            cursor = cursor.plusMinutes(intervalMinutes);
        }
        return starts;
    }

    /** research.md: index(i) = floor((i + 0.5) * M / N) for i = 0..N-1 - standard N-points-evenly-spaced-among-M-items formula. */
    private Set<Integer> computeEvenlySpacedIndices(int totalSlots, int bufferCount) {
        Set<Integer> indices = new HashSet<>();
        if (totalSlots == 0 || bufferCount <= 0) {
            return indices;
        }
        for (int i = 0; i < bufferCount; i++) {
            int index = (int) Math.floor((i + 0.5) * totalSlots / bufferCount);
            indices.add(Math.min(index, totalSlots - 1));
        }
        return indices;
    }
}
