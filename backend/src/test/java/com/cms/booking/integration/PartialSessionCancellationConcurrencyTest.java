package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.booking.domain.Booking;
import com.cms.booking.service.BookingCancellationService;
import com.cms.booking.exception.BookingNotCancellableException;
import com.cms.booking.domain.BookingStatus;
import com.cms.booking.service.SessionPartialCancellationService;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 030 US1 (P1), Edge Cases, convergence finding F1: partial cutoff cancellation racing an individual 025 cancellation on the same in-range Booking - exactly one wins it. */
class PartialSessionCancellationConcurrencyTest extends AbstractPartialSessionCancellationIntegrationTest {

    @Autowired
    private BookingCancellationService bookingCancellationService;

    @Autowired
    private SessionPartialCancellationService sessionPartialCancellationService;

    @Test
    void partialCutoffCancellationAndIndividualCancellationRacingOnTheSameBookingOnlyOneWins() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        List<Slot> slots = slotRepository.findBySession_Id(session.getId());
        Slot inRangeSlot = slots.stream().filter(s -> s.getStartTime().equals(LocalTime.of(11, 0))).findFirst().orElseThrow();
        Booking booking = bookSlot(clinic, doctor, inRangeSlot);

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
            Callable<Boolean> partialCancel = () -> {
                Session freshSession = sessionRepository.findById(session.getId()).orElseThrow();
                return sessionPartialCancellationService.cancelFromCutoff(
                                freshSession, LocalTime.of(10, 0), null, doctor.getAccount().getId())
                        == 1;
            };

            List<Future<Boolean>> futures = executor.invokeAll(List.of(individualCancel, partialCancel));
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
