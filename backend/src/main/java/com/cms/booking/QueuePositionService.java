package com.cms.booking;

import com.cms.scheduling.ScheduleMode;
import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotRepository;
import com.cms.scheduling.SlotStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 027: the shared computation both {@link StaffQueuePositionController} and
 * {@link PatientQueuePositionController} call - no authorization here, that's each
 * caller-side controller's own job (research.md R4). Computed fresh on every call, never
 * stored (research.md R2). Takes an already-fetched {@link Booking} since each caller has
 * already applied its own clinic/ownership filter to obtain one (convergence finding F1 -
 * no caller needs a bare-bookingId overload).
 */
@Service
public class QueuePositionService {

    private final SlotRepository slotRepository;

    public QueuePositionService(SlotRepository slotRepository) {
        this.slotRepository = slotRepository;
    }

    @Transactional(readOnly = true)
    public QueuePosition positionOf(Booking booking) {
        Slot thisSlot = booking.getSlot();

        if (thisSlot.getSession().getMode() != ScheduleMode.QUEUE) {
            return new QueuePosition(false, null);
        }
        if (thisSlot.getStatus() == SlotStatus.COMPLETED || thisSlot.getStatus() == SlotStatus.NO_SHOW) {
            return new QueuePosition(false, null);
        }

        int activeAhead = (int) slotRepository.findBySession_Id(thisSlot.getSession().getId()).stream()
                .filter(s -> s.getTokenNumber() != null && s.getTokenNumber() < thisSlot.getTokenNumber())
                .filter(s -> s.getStatus() == SlotStatus.BOOKED)
                .count();

        return new QueuePosition(true, activeAhead + 1);
    }

    public record QueuePosition(boolean applicable, Integer position) {}
}
