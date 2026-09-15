package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.booking.Booking;
import com.cms.booking.BookingCancellationService;
import com.cms.booking.BookingNotCancellableException;
import com.cms.booking.BookingStatus;
import com.cms.booking.SessionAlreadyCancelledException;
import com.cms.scheduling.Session;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 029 US1 (P1), FR-002/Edge Cases: whole-session cancellation racing an individual 025 cancellation on the same Booking - exactly one wins it. */
class SessionCancellationConcurrencyTest extends AbstractSessionCancellationIntegrationTest {

    @Autowired
    private BookingCancellationService bookingCancellationService;

    @Autowired
    private com.cms.booking.SessionCancellationService sessionCancellationService;

    @Test
    void wholeSessionCancellationAndIndividualCancellationRacingOnTheSameBookingOnlyOneWins() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        List<com.cms.scheduling.Slot> slots = slotRepository.findBySession_Id(session.getId());
        Booking booking = bookSlot(clinic, doctor, slots.get(0));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> individualCancel = () -> {
                Booking fresh = bookingRepository.findById(booking.getId()).orElseThrow();
                try {
                    bookingCancellationService.cancel(fresh);
                    return true;
                } catch (BookingNotCancellableException e) {
                    return false;
                }
            };
            Callable<Boolean> sessionCancel = () -> {
                Session freshSession = sessionRepository.findById(session.getId()).orElseThrow();
                try {
                    return sessionCancellationService.cancelSession(freshSession) == 1;
                } catch (SessionAlreadyCancelledException e) {
                    return false;
                }
            };

            List<Future<Boolean>> futures = executor.invokeAll(List.of(individualCancel, sessionCancel));
            long winCount = futures.stream().filter(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).count();

            assertThat(winCount).isEqualTo(1);
            assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
                    .isEqualTo(BookingStatus.CANCELLED);
        } finally {
            executor.shutdown();
        }
    }
}
