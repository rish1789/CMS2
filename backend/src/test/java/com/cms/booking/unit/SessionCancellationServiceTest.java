package com.cms.booking.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cms.booking.domain.Booking;
import com.cms.booking.domain.BookingStatus;
import com.cms.booking.exception.SessionAlreadyCancelledException;
import com.cms.booking.repository.BookingRepository;
import com.cms.booking.service.SessionCancellationService;
import com.cms.notification.service.NotificationEventService;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import com.cms.scheduling.repository.SlotRepository;
import com.cms.scheduling.service.SessionAvailabilityService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 065-phase1-stabilization (tasks.md T013/T022, FR-005/FR-009/FR-010): a whole-session cancellation
 * is durably recorded, succeeds on an empty session with 0, and is refused only when the session is
 * already whole-cancelled. Booking cancellation itself (notification, no waitlist event, slot back
 * to OPEN) is unchanged from spec 029.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionCancellationServiceTest {

    @Mock SlotRepository slotRepository;
    @Mock BookingRepository bookingRepository;
    @Mock NotificationEventService notificationEventService;
    @Mock SessionAvailabilityService sessionAvailabilityService;

    private final UUID callerId = UUID.randomUUID();
    private Session session;

    @BeforeEach
    void aFutureSession() {
        session = mock(Session.class);
        when(session.getId()).thenReturn(UUID.randomUUID());
        when(session.getSessionDate()).thenReturn(LocalDate.now().plusDays(1));
    }

    private SessionCancellationService service() {
        return new SessionCancellationService(
                slotRepository, bookingRepository, notificationEventService, sessionAvailabilityService);
    }

    private Slot slot(LocalTime start, SlotStatus status) {
        Slot slot = new Slot(session, start, start.plusMinutes(15));
        ReflectionTestUtils.setField(slot, "id", UUID.randomUUID());
        slot.setStatus(status);
        return slot;
    }

    private Booking activeBookingOn(Slot slot, UUID patientAccountIdOrNull) {
        Booking booking = mock(Booking.class, Mockito.RETURNS_DEEP_STUBS);
        when(booking.getId()).thenReturn(UUID.randomUUID());
        if (patientAccountIdOrNull == null) {
            when(booking.getPatient().getPatientAccount()).thenReturn(null);
        } else {
            when(booking.getPatient().getPatientAccount().getId()).thenReturn(patientAccountIdOrNull);
        }
        when(bookingRepository.findBySlot_IdAndStatus(slot.getId(), BookingStatus.ACTIVE)).thenReturn(Optional.of(booking));
        when(bookingRepository.cancelIfActive(booking.getId())).thenReturn(1);
        return booking;
    }

    @Test
    void bookedSlotsAreCancelledCountedAndTheWholeCancellationIsRecordedFirst() {
        Slot first = slot(LocalTime.of(9, 0), SlotStatus.BOOKED);
        Slot second = slot(LocalTime.of(9, 15), SlotStatus.BOOKED);
        Slot open = slot(LocalTime.of(9, 30), SlotStatus.OPEN);
        when(slotRepository.findBySession_Id(session.getId())).thenReturn(List.of(first, second, open));
        UUID patientAccountId = UUID.randomUUID();
        activeBookingOn(first, patientAccountId);
        activeBookingOn(second, null);

        int cancelled = service().cancelSession(session, callerId);

        assertThat(cancelled).isEqualTo(2);
        assertThat(first.getStatus()).isEqualTo(SlotStatus.OPEN);
        assertThat(second.getStatus()).isEqualTo(SlotStatus.OPEN);
        InOrder order = inOrder(sessionAvailabilityService, bookingRepository);
        order.verify(sessionAvailabilityService).recordWholeCancellation(session, callerId);
        order.verify(bookingRepository, Mockito.atLeastOnce()).cancelIfActive(any());
        // Only the linked patient is notified, with the session-cancellation event (029, unchanged).
        verify(notificationEventService).publish(eq(patientAccountId), eq("BOOKING_CANCELLED_SESSION"), anyString(), isNull());
        verify(notificationEventService, Mockito.times(1)).publish(any(), anyString(), anyString(), any());
    }

    @Test
    void anEmptySessionCancelsWithZeroAndIsStillRecorded() {
        when(slotRepository.findBySession_Id(session.getId())).thenReturn(List.of(slot(LocalTime.of(9, 0), SlotStatus.OPEN)));

        int cancelled = service().cancelSession(session, callerId);

        assertThat(cancelled).isZero();
        verify(sessionAvailabilityService).recordWholeCancellation(session, callerId);
        verify(bookingRepository, never()).cancelIfActive(any());
    }

    @Test
    void aSessionWithOnlyAttendedVisitsSucceedsAndLeavesThemUntouched() {
        Slot appeared = slot(LocalTime.of(9, 0), SlotStatus.APPEARED);
        Slot noShow = slot(LocalTime.of(9, 15), SlotStatus.NO_SHOW);
        when(slotRepository.findBySession_Id(session.getId())).thenReturn(List.of(appeared, noShow));

        int cancelled = service().cancelSession(session, callerId);

        assertThat(cancelled).isZero();
        assertThat(appeared.getStatus()).isEqualTo(SlotStatus.APPEARED);
        assertThat(noShow.getStatus()).isEqualTo(SlotStatus.NO_SHOW);
        verify(sessionAvailabilityService).recordWholeCancellation(session, callerId);
        verify(bookingRepository, never()).cancelIfActive(any());
    }

    @Test
    void anAlreadyWholeCancelledSessionIsRefusedAndNothingIsWritten() {
        when(sessionAvailabilityService.isWholeCancelled(session)).thenReturn(true);

        assertThatThrownBy(() -> service().cancelSession(session, callerId))
                .isInstanceOf(SessionAlreadyCancelledException.class);

        verify(sessionAvailabilityService, never()).recordWholeCancellation(any(), any());
        verifyNoInteractions(bookingRepository, notificationEventService);
    }

    @Test
    void losingAConcurrentWholeCancellationIsRefusedBeforeAnyBookingIsTouched() {
        Slot booked = slot(LocalTime.of(9, 0), SlotStatus.BOOKED);
        when(slotRepository.findBySession_Id(session.getId())).thenReturn(List.of(booked));
        activeBookingOn(booked, UUID.randomUUID());
        when(sessionAvailabilityService.recordWholeCancellation(session, callerId))
                .thenThrow(new DataIntegrityViolationException("uq_session_cancellation_whole"));

        assertThatThrownBy(() -> service().cancelSession(session, callerId))
                .isInstanceOf(SessionAlreadyCancelledException.class);

        verify(bookingRepository, never()).cancelIfActive(any());
        verifyNoInteractions(notificationEventService);
        assertThat(booked.getStatus()).isEqualTo(SlotStatus.BOOKED);
    }

    @Test
    void aSessionWithAnEarlierRangeCancellationCanStillBeWholeCancelled() {
        // isWholeCancelled is false: only a range record exists.
        when(slotRepository.findBySession_Id(session.getId())).thenReturn(List.of());

        assertThat(service().cancelSession(session, callerId)).isZero();
        verify(sessionAvailabilityService).recordWholeCancellation(session, callerId);
    }
}
