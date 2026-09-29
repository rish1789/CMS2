package com.cms.booking.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.cms.booking.domain.Booking;
import com.cms.booking.dto.BatchCancelResponse;
import com.cms.booking.exception.BookingNotCancellableException;
import com.cms.booking.repository.BookingRepository;
import com.cms.booking.service.BatchBookingCancellationService;
import com.cms.booking.service.BookingCancellationService;
import com.cms.identity.clinic.Clinic;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 057-day-sheet-status-overhaul research.md Decision 7: unit slice (no Spring context, no DB)
 * for {@link BatchBookingCancellationService} - proves per-booking success/failure collection
 * without one failure blocking the rest, and that a booking outside the caller's clinic is
 * reported NOT_FOUND rather than leaking cross-clinic existence.
 */
@ExtendWith(MockitoExtension.class)
class BatchBookingCancellationServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingCancellationService bookingCancellationService;

    @Mock
    private Booking booking;

    @Mock
    private Slot slot;

    @Mock
    private Session session;

    @Mock
    private Clinic clinic;

    private final UUID clinicId = UUID.randomUUID();

    private BatchBookingCancellationService service() {
        return new BatchBookingCancellationService(bookingRepository, bookingCancellationService);
    }

    private void stubBookingInClinic(UUID bookingId) {
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(booking.getSlot()).thenReturn(slot);
        when(slot.getSession()).thenReturn(session);
        when(session.getClinic()).thenReturn(clinic);
        when(clinic.getId()).thenReturn(clinicId);
    }

    @Test
    void allBookingsSucceedAreAllReportedCancelled() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        stubBookingInClinic(id1);
        when(bookingRepository.findById(id2)).thenReturn(Optional.of(booking));

        BatchCancelResponse response = service().cancelAll(clinicId, List.of(id1, id2));

        assertThat(response.cancelled()).containsExactlyInAnyOrder(id1, id2);
        assertThat(response.failed()).isEmpty();
    }

    @Test
    void oneUncancellableBookingIsReportedWithoutBlockingTheRestOfTheBatch() {
        UUID succeedsId = UUID.randomUUID();
        UUID failsId = UUID.randomUUID();
        stubBookingInClinic(succeedsId);
        when(bookingRepository.findById(failsId)).thenReturn(Optional.of(booking));
        when(bookingCancellationService.cancel(booking))
                .thenReturn(booking) // first call (succeedsId) succeeds
                .thenThrow(new BookingNotCancellableException(failsId)); // second call (failsId) fails

        BatchCancelResponse response = service().cancelAll(clinicId, List.of(succeedsId, failsId));

        assertThat(response.cancelled()).containsExactly(succeedsId);
        assertThat(response.failed()).hasSize(1);
        assertThat(response.failed().get(0).bookingId()).isEqualTo(failsId);
        assertThat(response.failed().get(0).reason()).isEqualTo("BOOKING_NOT_CANCELLABLE");
    }

    @Test
    void aBookingBelongingToAnotherClinicIsReportedNotFound() {
        UUID otherClinicBookingId = UUID.randomUUID();
        when(bookingRepository.findById(otherClinicBookingId)).thenReturn(Optional.of(booking));
        when(booking.getSlot()).thenReturn(slot);
        when(slot.getSession()).thenReturn(session);
        when(session.getClinic()).thenReturn(clinic);
        when(clinic.getId()).thenReturn(UUID.randomUUID()); // a different clinic than the caller's

        BatchCancelResponse response = service().cancelAll(clinicId, List.of(otherClinicBookingId));

        assertThat(response.cancelled()).isEmpty();
        assertThat(response.failed()).hasSize(1);
        assertThat(response.failed().get(0).reason()).isEqualTo("NOT_FOUND");
        org.mockito.Mockito.verify(bookingCancellationService, org.mockito.Mockito.never()).cancel(eq(booking));
    }
}
