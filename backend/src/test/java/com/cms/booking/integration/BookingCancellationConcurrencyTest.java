package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.booking.Booking;
import com.cms.booking.BookingCancelledEvent;
import com.cms.booking.BookingCancellationService;
import com.cms.booking.BookingNotCancellableException;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;

/** 028 US1 (P1), FR-008/SC-003/SC-005: the data-layer race-closure, mirroring 016/020's genuine-concurrency precedent. */
@Import(BookingCancellationConcurrencyTest.RecordingListenerConfig.class)
class BookingCancellationConcurrencyTest extends AbstractBookingCancellationIntegrationTest {

    @Autowired
    private BookingCancellationService bookingCancellationService;

    @Autowired
    private RecordingListener recordingListener;

    @Test
    void tenConcurrentCancellationAttemptsAgainstOneBookingOnlyOneSucceedsAndOnlyOneEventFires() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Booking booking = saveConfirmedBookingAt(clinic, doctor, LocalTime.now().plusHours(3));
        recordingListener.events.clear();

        ExecutorService executor = Executors.newFixedThreadPool(10);
        try {
            List<Callable<Boolean>> attempts = java.util.stream.IntStream.range(0, 10)
                    .<Callable<Boolean>>mapToObj(i -> () -> {
                        Booking freshRead =
                                bookingRepository.findById(booking.getId()).orElseThrow();
                        try {
                            bookingCancellationService.cancel(freshRead);
                            return true;
                        } catch (BookingNotCancellableException e) {
                            return false;
                        }
                    })
                    .toList();

            List<Future<Boolean>> futures = executor.invokeAll(attempts);
            long successCount = 0;
            for (Future<Boolean> f : futures) {
                if (f.get()) {
                    successCount++;
                }
            }

            assertThat(successCount).isEqualTo(1);
            assertThat(recordingListener.events).hasSize(1);
            assertThat(recordingListener.events.get(0).bookingId()).isEqualTo(booking.getId());
        } finally {
            executor.shutdown();
        }
    }

    /** Test-only listener double, mirrors 037's own recording-listener precedent for asserting exact event counts without log-scraping. */
    @TestConfiguration
    static class RecordingListenerConfig {
        @Bean
        RecordingListener recordingListener() {
            return new RecordingListener();
        }
    }

    static class RecordingListener {
        final List<BookingCancelledEvent> events = new CopyOnWriteArrayList<>();

        @EventListener
        void on(BookingCancelledEvent event) {
            events.add(event);
        }
    }
}
