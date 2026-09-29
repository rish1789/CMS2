package com.cms.booking.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cms.booking.domain.Booking;
import com.cms.booking.domain.BookingStatus;
import com.cms.booking.exception.InvalidCancellationRangeException;
import com.cms.booking.exception.SessionAlreadyCancelledException;
import com.cms.booking.repository.BookingRepository;
import com.cms.booking.service.SessionPartialCancellationService;
import com.cms.notification.service.NotificationEventService;
import com.cms.scheduling.domain.ScheduleMode;
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
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 065-phase1-stabilization (tasks.md T014, FR-005/FR-007/FR-011): every range cancellation is
 * durably recorded - even one that cancels no booking - so the range stays unbookable. A session
 * that is already whole-cancelled refuses further range cancellations.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionPartialCancellationServiceTest {

    @Mock SlotRepository slotRepository;
    @Mock BookingRepository bookingRepository;
    @Mock NotificationEventService notificationEventService;
    @Mock SessionAvailabilityService sessionAvailabilityService;

    private final UUID callerId = UUID.randomUUID();
    private Session session;

    @BeforeEach
    void aFixedTimeSessionTomorrow() {
        session = mock(Session.class);
        when(session.getId()).thenReturn(UUID.randomUUID());
        when(session.getMode()).thenReturn(ScheduleMode.FIXED_TIME);
        when(session.getSessionDate()).thenReturn(LocalDate.now().plusDays(1));
    }

    private SessionPartialCancellationService service() {
        return new SessionPartialCancellationService(
                slotRepository, bookingRepository, notificationEventService, sessionAvailabilityService);
    }

    private Slot bookedAt(LocalTime start) {
        Slot slot = new Slot(session, start, start.plusMinutes(15));
        ReflectionTestUtils.setField(slot, "id", UUID.randomUUID());
        slot.setStatus(SlotStatus.BOOKED);
        Booking booking = mock(Booking.class, Mockito.RETURNS_DEEP_STUBS);
        when(booking.getId()).thenReturn(UUID.randomUUID());
        when(booking.getPatient().getPatientAccount()).thenReturn(null);
        when(bookingRepository.findBySlot_IdAndStatus(slot.getId(), BookingStatus.ACTIVE)).thenReturn(Optional.of(booking));
        when(bookingRepository.cancelIfActive(booking.getId())).thenReturn(1);
        return slot;
    }

    @Test
    void theRangeIsRecordedWithItsCutoffAndToTime() {
        Slot before = bookedAt(LocalTime.of(10, 0));
        Slot inside = bookedAt(LocalTime.of(11, 0));
        Slot atToTime = bookedAt(LocalTime.of(12, 0));
        when(slotRepository.findBySession_Id(session.getId())).thenReturn(List.of(before, inside, atToTime));

        int cancelled = service().cancelFromCutoff(session, LocalTime.of(11, 0), LocalTime.of(12, 0), callerId);

        assertThat(cancelled).isEqualTo(1);
        assertThat(inside.getStatus()).isEqualTo(SlotStatus.OPEN);
        assertThat(before.getStatus()).isEqualTo(SlotStatus.BOOKED);
        assertThat(atToTime.getStatus()).isEqualTo(SlotStatus.BOOKED);
        verify(sessionAvailabilityService)
                .recordRangeCancellation(session, LocalTime.of(11, 0), LocalTime.of(12, 0), callerId);
    }

    @Test
    void anOpenEndedRangeIsRecordedWithANullToTime() {
        when(slotRepository.findBySession_Id(session.getId())).thenReturn(List.of());

        service().cancelFromCutoff(session, LocalTime.of(11, 0), null, callerId);

        verify(sessionAvailabilityService).recordRangeCancellation(session, LocalTime.of(11, 0), null, callerId);
    }

    @Test
    void zeroQualifyingBookingsStillRecordsTheRange() {
        Slot beforeCutoff = bookedAt(LocalTime.of(9, 0));
        when(slotRepository.findBySession_Id(session.getId())).thenReturn(List.of(beforeCutoff));

        int cancelled = service().cancelFromCutoff(session, LocalTime.of(11, 0), null, callerId);

        assertThat(cancelled).isZero();
        verify(sessionAvailabilityService).recordRangeCancellation(session, LocalTime.of(11, 0), null, callerId);
    }

    @Test
    void aWholeCancelledSessionRefusesARangeAndWritesNothing() {
        when(sessionAvailabilityService.isWholeCancelled(session)).thenReturn(true);

        assertThatThrownBy(() -> service().cancelFromCutoff(session, LocalTime.of(11, 0), null, callerId))
                .isInstanceOf(SessionAlreadyCancelledException.class);

        verify(sessionAvailabilityService, never()).recordRangeCancellation(any(), any(), any(), any());
        verifyNoInteractions(bookingRepository, notificationEventService);
    }

    @Test
    void anInvalidRangeIsRejectedBeforeAnythingIsRecorded() {
        assertThatThrownBy(() -> service().cancelFromCutoff(session, LocalTime.of(12, 0), LocalTime.of(11, 0), callerId))
                .isInstanceOf(InvalidCancellationRangeException.class);

        verify(sessionAvailabilityService, never()).recordRangeCancellation(any(), any(), any(), any());
    }

    @Test
    void repeatedRangesAreEachRecorded() {
        when(slotRepository.findBySession_Id(session.getId())).thenReturn(List.of());

        service().cancelFromCutoff(session, LocalTime.of(9, 0), LocalTime.of(10, 0), callerId);
        service().cancelFromCutoff(session, LocalTime.of(12, 0), null, callerId);

        verify(sessionAvailabilityService)
                .recordRangeCancellation(session, LocalTime.of(9, 0), LocalTime.of(10, 0), callerId);
        verify(sessionAvailabilityService).recordRangeCancellation(session, LocalTime.of(12, 0), null, callerId);
    }
}
