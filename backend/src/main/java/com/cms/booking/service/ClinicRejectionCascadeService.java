package com.cms.booking.service;

import com.cms.booking.domain.Booking;
import com.cms.booking.domain.BookingCancellationReason;
import com.cms.booking.repository.BookingRepository;
import com.cms.notification.service.NotificationEventService;
import com.cms.scheduling.domain.SlotStatus;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 062-rejected-clinic-gating (FR-009/FR-010, research.md Decision 4): cancels a rejected clinic's
 * upcoming bookings with the system-only {@link BookingCancellationReason#CLINIC_REJECTED} reason
 * and frees their slots. Deliberately never goes through {@link BookingCancellationService} - that
 * is 025's waitlist-bump path, and a rejected clinic can't serve a waitlisted patient. Each
 * cancelled booking's linked Patient Account is notified once; walk-ins without one are skipped.
 *
 * <p>{@code REQUIRES_NEW} is mandatory, not stylistic (analyze finding C1): this runs from an
 * AFTER_COMMIT listener, where a REQUIRED transaction would join the already-committed one and its
 * writes would silently never persist.
 */
@Service
public class ClinicRejectionCascadeService {

    private static final Logger log = LoggerFactory.getLogger(ClinicRejectionCascadeService.class);
    private static final String EVENT_TYPE = "BOOKING_CANCELLED_CLINIC_REJECTED";

    private final BookingRepository bookingRepository;
    private final NotificationEventService notificationEventService;

    public ClinicRejectionCascadeService(
            BookingRepository bookingRepository, NotificationEventService notificationEventService) {
        this.bookingRepository = bookingRepository;
        this.notificationEventService = notificationEventService;
    }

    /** Returns how many bookings this call actually cancelled. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int cascadeFromClinic(UUID clinicId) {
        int cancelled = 0;
        for (Booking booking : bookingRepository.findActiveUpcomingBookingsByClinic(clinicId, LocalDate.now())) {
            if (bookingRepository.cancelIfActive(booking.getId(), BookingCancellationReason.CLINIC_REJECTED, null) == 0) {
                // Lost a race to a concurrent action already resolving this Booking - not an
                // error for a bulk sweep, simply skip it (029/030/033's own precedent).
                continue;
            }
            booking.getSlot().setStatus(SlotStatus.OPEN);
            notifyPatient(booking);
            cancelled++;
        }
        log.info("Clinic rejection cascade: clinicId={}, cancelledBookings={}", clinicId, cancelled);
        return cancelled;
    }

    private void notifyPatient(Booking booking) {
        if (booking.getPatient().getPatientAccount() == null) {
            return;
        }
        notificationEventService.publish(
                booking.getPatient().getPatientAccount().getId(),
                EVENT_TYPE,
                "{\"bookingId\":\"" + booking.getId() + "\"}",
                null);
    }
}
