package com.cms.booking.service;

import com.cms.booking.domain.Booking;
import com.cms.booking.dto.BatchCancelResponse;
import com.cms.booking.exception.BookingNotCancellableException;
import com.cms.booking.exception.BookingNotFoundException;
import com.cms.booking.repository.BookingRepository;
import com.cms.scheduling.exception.NotAFixedTimeSessionException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 057-day-sheet-status-overhaul research.md Decision 7: delegates to {@link
 * BookingCancellationService#cancel(Booking)} once per booking id - each call is already its
 * own {@code REQUIRES_NEW} transaction (033-deverification-cascade's existing design, built for
 * exactly this batch scenario), so one booking's failure never rolls back or blocks the others.
 * Authorization (ClinicAdmin/Operations-only) is checked once by the caller-side controller,
 * not here, mirroring {@code StaffBookingCancellationController}'s existing split for the
 * single-booking endpoint.
 */
@Service
public class BatchBookingCancellationService {

    private final BookingRepository bookingRepository;
    private final BookingCancellationService bookingCancellationService;

    public BatchBookingCancellationService(
            BookingRepository bookingRepository, BookingCancellationService bookingCancellationService) {
        this.bookingRepository = bookingRepository;
        this.bookingCancellationService = bookingCancellationService;
    }

    public BatchCancelResponse cancelAll(UUID clinicId, List<UUID> bookingIds) {
        List<UUID> cancelled = new ArrayList<>();
        List<BatchCancelResponse.Failure> failed = new ArrayList<>();

        for (UUID bookingId : bookingIds) {
            try {
                Booking booking = bookingRepository
                        .findById(bookingId)
                        .filter(b -> b.getSlot().getSession().getClinic().getId().equals(clinicId))
                        .orElseThrow(() -> new BookingNotFoundException(bookingId));
                bookingCancellationService.cancel(booking);
                cancelled.add(bookingId);
            } catch (BookingNotFoundException e) {
                failed.add(new BatchCancelResponse.Failure(bookingId, "NOT_FOUND"));
            } catch (BookingNotCancellableException e) {
                failed.add(new BatchCancelResponse.Failure(bookingId, "BOOKING_NOT_CANCELLABLE"));
            } catch (NotAFixedTimeSessionException e) {
                failed.add(new BatchCancelResponse.Failure(bookingId, "NOT_A_FIXED_TIME_SESSION"));
            }
        }

        return new BatchCancelResponse(cancelled, failed);
    }
}
