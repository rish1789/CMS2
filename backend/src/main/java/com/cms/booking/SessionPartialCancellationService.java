package com.cms.booking;

import com.cms.notification.NotificationEventService;
import com.cms.scheduling.ScheduleMode;
import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotRepository;
import com.cms.scheduling.SlotStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 030: cancels every currently-active Booking whose Slot is scheduled at or after a
 * staff-chosen cutoff time - the trailing-portion analog of 029's whole-session action, sharing
 * its core mechanics (never publishes {@link BookingCancelledEvent}, notifies linked Patients
 * only) but adding a per-mode time filter (research.md R1/R3) and never rejecting a
 * zero-qualifying cutoff as an error (research.md R2, unlike 029).
 *
 * <p>042-day-sheet-hardening follow-up: an optional {@code toTime} bounds the range on the
 * upper end too (exclusive), so staff can clear a single mid-session block instead of always
 * cancelling through to the end of the session. {@code null} preserves the original open-ended
 * behavior exactly.
 */
@Service
public class SessionPartialCancellationService {

    private static final String EVENT_TYPE = "BOOKING_CANCELLED_PARTIAL";

    private final SlotRepository slotRepository;
    private final BookingRepository bookingRepository;
    private final NotificationEventService notificationEventService;

    public SessionPartialCancellationService(
            SlotRepository slotRepository, BookingRepository bookingRepository, NotificationEventService notificationEventService) {
        this.slotRepository = slotRepository;
        this.bookingRepository = bookingRepository;
        this.notificationEventService = notificationEventService;
    }

    @Transactional
    public int cancelFromCutoff(Session session, LocalTime cutoffTime, LocalTime toTime) {
        if (toTime != null && !toTime.isAfter(cutoffTime)) {
            throw new InvalidCancellationRangeException();
        }

        List<Slot> qualifyingSlots = slotRepository.findBySession_Id(session.getId()).stream()
                .filter(s -> s.getStatus() == SlotStatus.BOOKED)
                .filter(s -> isAtOrAfterCutoff(session, s, cutoffTime))
                .filter(s -> toTime == null || isBefore(session, s, toTime))
                .toList();

        int cancelledCount = 0;
        for (Slot slot : qualifyingSlots) {
            Booking booking = bookingRepository
                    .findBySlot_IdAndStatus(slot.getId(), BookingStatus.ACTIVE)
                    .orElse(null);
            if (booking == null) {
                continue;
            }

            int updated = bookingRepository.cancelIfActive(booking.getId());
            if (updated == 0) {
                // Lost a race to a concurrent action (025's individual cancellation, 029's
                // whole-session cancellation, or a walk-in reclaim) - not an error for a bulk
                // operation, simply skip it (research.md R1, same as 029's R4).
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
            // FR-005: no BookingCancelledEvent published anywhere in this path.
        }

        // FR-009/research.md R2: zero qualifying (or zero actually won) is a normal success,
        // never a rejection - deliberately no exception thrown here, unlike 029.
        return cancelledCount;
    }

    /** research.md R3: Fixed-Time compares startTime as a LocalDateTime; Queue-mode compares createdAt as an Instant. */
    private boolean isAtOrAfterCutoff(Session session, Slot slot, LocalTime cutoffTime) {
        if (session.getMode() == ScheduleMode.FIXED_TIME) {
            LocalDateTime scheduledAt = LocalDateTime.of(session.getSessionDate(), slot.getStartTime());
            LocalDateTime threshold = LocalDateTime.of(session.getSessionDate(), cutoffTime);
            return !scheduledAt.isBefore(threshold);
        }

        Instant thresholdInstant =
                LocalDateTime.of(session.getSessionDate(), cutoffTime).atZone(ZoneId.systemDefault()).toInstant();
        return !slot.getCreatedAt().isBefore(thresholdInstant);
    }

    /** Mirrors {@link #isAtOrAfterCutoff} for the optional upper bound - exclusive, symmetric with the inclusive lower bound. */
    private boolean isBefore(Session session, Slot slot, LocalTime toTime) {
        if (session.getMode() == ScheduleMode.FIXED_TIME) {
            LocalDateTime scheduledAt = LocalDateTime.of(session.getSessionDate(), slot.getStartTime());
            LocalDateTime threshold = LocalDateTime.of(session.getSessionDate(), toTime);
            return scheduledAt.isBefore(threshold);
        }

        Instant thresholdInstant =
                LocalDateTime.of(session.getSessionDate(), toTime).atZone(ZoneId.systemDefault()).toInstant();
        return slot.getCreatedAt().isBefore(thresholdInstant);
    }
}
