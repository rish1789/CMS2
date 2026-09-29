package com.cms.booking.service;

import com.cms.booking.domain.Booking;
import com.cms.booking.domain.BookingCancelledEvent;
import com.cms.booking.domain.BookingStatus;
import com.cms.booking.exception.SessionAlreadyCancelledException;
import com.cms.booking.repository.BookingRepository;


import com.cms.notification.service.NotificationEventService;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.repository.SlotRepository;
import com.cms.scheduling.domain.SlotStatus;
import com.cms.scheduling.service.SessionAvailabilityService;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 029: cancels every currently-active Booking in a Session in one action - applies both to
 * Fixed-Time and Queue-mode Sessions, and deliberately never publishes 028's
 * {@link BookingCancelledEvent} (research.md R2) - so it calls {@code cancelIfActive} directly
 * rather than 028's {@link BookingCancellationService#cancel}. First real production caller of
 * {@link NotificationEventService#publish} (research.md R5).
 *
 * <p>065-phase1-stabilization (FR-005/FR-009): reverses 029's "no session-level concept"
 * (Clarification Q1). The cancellation is now durably recorded, which is what makes the session
 * unbookable afterwards ({@link SessionAvailabilityService}). An empty session cancels with 0; only
 * an already whole-cancelled session is refused. Cancelled bookings' slots still return to OPEN -
 * slot status semantics are unchanged.
 */
@Service
public class SessionCancellationService {

    private static final String EVENT_TYPE = "BOOKING_CANCELLED_SESSION";

    private final SlotRepository slotRepository;
    private final BookingRepository bookingRepository;
    private final NotificationEventService notificationEventService;
    private final SessionAvailabilityService sessionAvailabilityService;

    public SessionCancellationService(
            SlotRepository slotRepository,
            BookingRepository bookingRepository,
            NotificationEventService notificationEventService,
            SessionAvailabilityService sessionAvailabilityService) {
        this.slotRepository = slotRepository;
        this.bookingRepository = bookingRepository;
        this.notificationEventService = notificationEventService;
        this.sessionAvailabilityService = sessionAvailabilityService;
    }

    @Transactional
    public int cancelSession(Session session, UUID cancelledByAccountId) {
        // 065 FR-009: "already cancelled" means a whole-session record exists - no longer "zero
        // BOOKED slots", which wrongly refused an empty session and allowed a repeat (BUG-004).
        if (sessionAvailabilityService.isWholeCancelled(session)) {
            throw new SessionAlreadyCancelledException(session.getId());
        }
        // Recorded first: a concurrent second cancellation fails on uq_session_cancellation_whole
        // here, before it touches any booking - the loser of the race changes nothing.
        try {
            sessionAvailabilityService.recordWholeCancellation(session, cancelledByAccountId);
        } catch (DataIntegrityViolationException e) {
            throw new SessionAlreadyCancelledException(session.getId());
        }

        List<Slot> bookedSlots = slotRepository.findBySession_Id(session.getId()).stream()
                .filter(s -> s.getStatus() == SlotStatus.BOOKED)
                .toList();

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
