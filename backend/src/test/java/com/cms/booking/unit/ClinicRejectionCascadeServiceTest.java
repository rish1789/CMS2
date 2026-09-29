package com.cms.booking.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cms.booking.domain.Booking;
import com.cms.booking.domain.BookingCancellationReason;
import com.cms.booking.repository.BookingRepository;
import com.cms.booking.service.BookingCancellationService;
import com.cms.booking.service.ClinicRejectionCascadeService;
import com.cms.notification.service.NotificationEventService;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 062-rejected-clinic-gating (FR-009/FR-010, research.md Decision 4, tasks.md T008): rejecting a
 * clinic cancels its upcoming bookings with the system-only CLINIC_REJECTED reason, frees their
 * slots, and notifies patients who have an account - without ever going through
 * BookingCancellationService, so no waitlist offer is triggered for a clinic that can't serve it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClinicRejectionCascadeServiceTest {

    private static final String EVENT_TYPE = "BOOKING_CANCELLED_CLINIC_REJECTED";

    @Mock BookingRepository bookingRepository;
    @Mock NotificationEventService notificationEventService;
    @Mock BookingCancellationService bookingCancellationService;

    private ClinicRejectionCascadeService service;
    private final UUID clinicId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ClinicRejectionCascadeService(bookingRepository, notificationEventService);
    }

    /** A booked slot's booking; {@code patientAccountId} null models a walk-in with no patient login. */
    private Booking booking(UUID patientAccountId) {
        Booking booking = mock(Booking.class, RETURNS_DEEP_STUBS);
        UUID id = UUID.randomUUID();
        when(booking.getId()).thenReturn(id);
        Slot slot = mock(Slot.class);
        when(booking.getSlot()).thenReturn(slot);
        if (patientAccountId == null) {
            when(booking.getPatient().getPatientAccount()).thenReturn(null);
        } else {
            when(booking.getPatient().getPatientAccount().getId()).thenReturn(patientAccountId);
        }
        when(bookingRepository.cancelIfActive(id, BookingCancellationReason.CLINIC_REJECTED, null))
                .thenReturn(1);
        return booking;
    }

    @Test
    void cancelsEachUpcomingBookingWithTheClinicRejectedReasonAndFreesItsSlot() {
        Booking first = booking(UUID.randomUUID());
        Booking second = booking(null);
        when(bookingRepository.findActiveUpcomingBookingsByClinic(eq(clinicId), any(LocalDate.class)))
                .thenReturn(List.of(first, second));

        int cancelled = service.cascadeFromClinic(clinicId);

        assertThat(cancelled).isEqualTo(2);
        verify(bookingRepository).cancelIfActive(first.getId(), BookingCancellationReason.CLINIC_REJECTED, null);
        verify(bookingRepository).cancelIfActive(second.getId(), BookingCancellationReason.CLINIC_REJECTED, null);
        verify(first.getSlot()).setStatus(SlotStatus.OPEN);
        verify(second.getSlot()).setStatus(SlotStatus.OPEN);
    }

    @Test
    void queriesUpcomingBookingsFromTodayOnward() {
        when(bookingRepository.findActiveUpcomingBookingsByClinic(eq(clinicId), any(LocalDate.class)))
                .thenReturn(List.of());

        service.cascadeFromClinic(clinicId);

        verify(bookingRepository).findActiveUpcomingBookingsByClinic(clinicId, LocalDate.now());
    }

    @Test
    void notifiesOnlyPatientsWhoHaveAnAccount() {
        UUID accountId = UUID.randomUUID();
        Booking withAccount = booking(accountId);
        Booking walkIn = booking(null);
        when(bookingRepository.findActiveUpcomingBookingsByClinic(eq(clinicId), any(LocalDate.class)))
                .thenReturn(List.of(withAccount, walkIn));

        service.cascadeFromClinic(clinicId);

        verify(notificationEventService, times(1)).publish(eq(accountId), eq(EVENT_TYPE), anyString(), isNull());
    }

    @Test
    void neverGoesThroughTheWaitlistTriggeringCancellationPath() {
        Booking booking = booking(UUID.randomUUID());
        when(bookingRepository.findActiveUpcomingBookingsByClinic(eq(clinicId), any(LocalDate.class)))
                .thenReturn(List.of(booking));

        service.cascadeFromClinic(clinicId);

        verifyNoInteractions(bookingCancellationService);
    }

    @Test
    void aBookingAlreadyResolvedByAConcurrentActionIsSkippedWithoutError() {
        Booking lostRace = booking(UUID.randomUUID());
        when(bookingRepository.cancelIfActive(lostRace.getId(), BookingCancellationReason.CLINIC_REJECTED, null))
                .thenReturn(0);
        when(bookingRepository.findActiveUpcomingBookingsByClinic(eq(clinicId), any(LocalDate.class)))
                .thenReturn(List.of(lostRace));

        int cancelled = service.cascadeFromClinic(clinicId);

        assertThat(cancelled).isZero();
        verify(lostRace.getSlot(), never()).setStatus(any());
        verify(notificationEventService, never()).publish(any(), any(), any(), any());
    }
}
