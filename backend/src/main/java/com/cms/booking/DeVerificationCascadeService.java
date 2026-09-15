package com.cms.booking;

import com.cms.inbox.InboxItemService;
import com.cms.notification.NotificationEventService;
import com.cms.scheduling.ScheduleMode;
import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 033: cancels every still-pending Booking tied to a de-verified Clinic (FR-001) or a
 * license-revoked Doctor (FR-003). Branches per Session mode (research.md R4) - Fixed-Time
 * bookings go through {@link BookingCancellationService#cancel}, the real 025 cancellation path
 * (real waitlist-bump trigger, FR-004); Queue-mode bookings use a direct
 * {@code cancelIfActive}-based path mirroring 029/030's own non-025 bulk-cancellation shape
 * (no waitlist bump, FR-005 - 028's matching stays exclusively fixed-time). Every successfully
 * cancelled booking's linked Patient Account is notified once, regardless of mode (FR-006).
 */
@Service
public class DeVerificationCascadeService {

    private static final String EVENT_TYPE = "BOOKING_CANCELLED_DEVERIFICATION";

    private final BookingRepository bookingRepository;
    private final BookingCancellationService bookingCancellationService;
    private final NotificationEventService notificationEventService;
    private final InboxItemService inboxItemService;

    public DeVerificationCascadeService(
            BookingRepository bookingRepository,
            BookingCancellationService bookingCancellationService,
            NotificationEventService notificationEventService,
            InboxItemService inboxItemService) {
        this.bookingRepository = bookingRepository;
        this.bookingCancellationService = bookingCancellationService;
        this.notificationEventService = notificationEventService;
        this.inboxItemService = inboxItemService;
    }

    /** FR-001: every still-pending booking at this clinic, across every doctor. */
    @Transactional
    public void cascadeFromClinic(UUID clinicId) {
        List<Booking> cancelled = cancelBatch(bookingRepository.findActiveFutureBookingsByClinic(clinicId));
        // 038-unified-realtime-inbox FR-004: no single doctor identifies a clinic-wide
        // de-verification (it can span every doctor at the clinic) - doctorName stays null,
        // tolerated by InboxItemResponse's summary assembly (research.md R7).
        createCascadeNotices(null, cancelled);
    }

    /** FR-003: every still-pending booking for this doctor, across every clinic they're staffed at. */
    @Transactional
    public void cascadeFromDoctor(UUID doctorProfileId) {
        List<Booking> cancelled = cancelBatch(bookingRepository.findActiveFutureBookingsByDoctor(doctorProfileId));
        String doctorName = cancelled.isEmpty()
                ? null
                : cancelled.get(0).getSlot().getSession().getDoctorProfile().getAccount().getName();
        createCascadeNotices(doctorName, cancelled);
    }

    /** research.md R7: one Inbox Item per affected clinic, grouped from the bookings this cascade actually cancelled. */
    private void createCascadeNotices(String doctorName, List<Booking> cancelledBookings) {
        if (cancelledBookings.isEmpty()) {
            return;
        }
        Map<UUID, List<Booking>> byClinic = cancelledBookings.stream()
                .collect(Collectors.groupingBy(b -> b.getSlot().getSession().getClinic().getId()));
        inboxItemService.createCascadeNotices(doctorName, byClinic);
    }

    private List<Booking> cancelBatch(List<Booking> bookings) {
        List<Booking> cancelled = new ArrayList<>();
        for (Booking booking : bookings) {
            if (!cancelOne(booking)) {
                // Lost a race to a concurrent action already resolving this same Booking - not
                // an error for a bulk operation, simply skip it (mirrors 029/030's own precedent).
                continue;
            }
            notifyPatient(booking);
            cancelled.add(booking);
        }
        return cancelled;
    }

    private boolean cancelOne(Booking booking) {
        Slot slot = booking.getSlot();
        if (slot.getSession().getMode() == ScheduleMode.FIXED_TIME) {
            try {
                bookingCancellationService.cancel(booking);
                return true;
            } catch (BookingNotCancellableException e) {
                return false;
            }
        }

        int updated = bookingRepository.cancelIfActive(booking.getId());
        if (updated == 0) {
            return false;
        }
        slot.setStatus(SlotStatus.OPEN);
        return true;
    }

    private void notifyPatient(Booking booking) {
        UUID patientAccountId = booking.getPatient().getPatientAccount() != null
                ? booking.getPatient().getPatientAccount().getId()
                : null;
        if (patientAccountId != null) {
            notificationEventService.publish(
                    patientAccountId, EVENT_TYPE, "{\"bookingId\":\"" + booking.getId() + "\"}", null);
        }
    }
}
