package com.cms.booking.service;

import com.cms.booking.api.PatientQueuePositionController;
import com.cms.booking.api.StaffQueuePositionController;
import com.cms.booking.domain.Booking;


import com.cms.scheduling.domain.ScheduleMode;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.repository.SlotRepository;
import com.cms.scheduling.domain.SlotStatus;
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

        // 063-front-desk-walk-in (research.md Decision 7): a Fixed-Time walk-in waits in its session's
        // untimed walk-in line and gets a position there; a timed appointment has no queue position.
        if (thisSlot.getSession().getMode() != ScheduleMode.QUEUE && !thisSlot.isUntimed()) {
            return new QueuePosition(false, null);
        }
        // 064-queue-send-in-complete (FR-005): a position only means something while the patient is
        // still waiting - not once they are in with the doctor, seen, marked no-show, or cancelled.
        if (thisSlot.getStatus() != SlotStatus.BOOKED) {
            return new QueuePosition(false, null);
        }

        int activeAhead = (int) slotRepository.findBySession_Id(thisSlot.getSession().getId()).stream()
                .filter(s -> s.isUntimed()
                        && s.getTokenNumber() != null
                        && s.getTokenNumber() < thisSlot.getTokenNumber())
                .filter(s -> s.getStatus() == SlotStatus.BOOKED)
                .count();

        return new QueuePosition(true, activeAhead + 1);
    }

    public record QueuePosition(boolean applicable, Integer position) {}
}
