package com.cms.booking;

import com.cms.notification.NotificationEventService;
import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotRepository;
import com.cms.scheduling.SlotStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 029: cancels every currently-active Booking in a Session in one action - applies both to
 * Fixed-Time and Queue-mode Sessions, and deliberately never publishes 028's
 * {@link BookingCancelledEvent} (research.md R2) - so it calls {@code cancelIfActive} directly
 * rather than 028's {@link BookingCancellationService#cancel}. First real production caller of
 * {@link NotificationEventService#publish} (research.md R5).
 */
@Service
public class SessionCancellationService {

    private static final String EVENT_TYPE = "BOOKING_CANCELLED_SESSION";

    private final SlotRepository slotRepository;
    private final BookingRepository bookingRepository;
    private final NotificationEventService notificationEventService;

    public SessionCancellationService(
            SlotRepository slotRepository, BookingRepository bookingRepository, NotificationEventService notificationEventService) {
        this.slotRepository = slotRepository;
        this.bookingRepository = bookingRepository;
        this.notificationEventService = notificationEventService;
    }

    @Transactional
    public int cancelSession(Session session) {
        List<Slot> bookedSlots = slotRepository.findBySession_Id(session.getId()).stream()
                .filter(s -> s.getStatus() == SlotStatus.BOOKED)
                .toList();

        // FR-005/research.md R3: no separate Session-level flag exists - "already cancelled"
        // (or "never had anything to cancel") is structurally defined as zero BOOKED Slots.
        if (bookedSlots.isEmpty()) {
            throw new SessionAlreadyCancelledException(session.getId());
        }

        int cancelledCount = 0;
        for (Slot slot : bookedSlots) {
            Booking booking = bookingRepository
                    .findBySlot_IdAndStatus(slot.getId(), BookingStatus.ACTIVE)
                    .orElse(null);
            if (booking == null) {
                continue;
            }

            int updated = bookingRepository.cancelIfActive(booking.getId());
            if (updated == 0) {
                // Lost a race to a concurrent action (e.g. 025's individual cancellation, or a
                // walk-in reclaim) already resolving this same Booking - not an error for a
                // bulk operation, simply skip it (research.md R4).
                continue;
            }

            slot.setStatus(SlotStatus.OPEN);
            cancelledCount++;

            UUID patientAccountId = booking.getPatient().getPatientAccount() != null
                    ? booking.getPatient().getPatientAccount().getId()
                    : null;
            if (patientAccountId != null) {
                notificationEventService.publish(
                        patientAccountId, EVENT_TYPE, "{\"bookingId\":\"" + booking.getId() + "\"}", null);
            }
            // FR-003: no BookingCancelledEvent published anywhere in this path.
        }

        return cancelledCount;
    }
}
