package com.cms.booking.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cms.booking.domain.Booking;
import com.cms.booking.domain.BookingCancelledEvent;
import com.cms.booking.exception.BookingNotCancellableException;
import com.cms.booking.repository.BookingRepository;
import com.cms.booking.service.BookingCancellationService;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import com.cms.scheduling.repository.SlotRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 048-backend-unit-tests US2: this exact class has a documented history of two real,
 * previously-shipped bugs (a detached-entity lost-update, a rollback-poisoning issue) - the
 * highest-value class in booking to protect with a fast, Docker-independent test loop. Pure
 * Mockito.
 */
@ExtendWith(MockitoExtension.class)
class BookingCancellationServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private SlotRepository slotRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private Booking booking;

    @Mock
    private Slot slot;

    private final UUID bookingId = UUID.randomUUID();
    private final UUID slotId = UUID.randomUUID();

    private BookingCancellationService newService() {
        return new BookingCancellationService(bookingRepository, slotRepository, eventPublisher);
    }

    /**
     * 064-queue-send-in-complete (FR-009): staff can now cancel a waiting queue booking - its token is
     * freed like any slot. The event is still published; WaitlistBumpListener ignores queue sessions,
     * so the freed token is never offered to the waitlist.
     */
    @Test
    void aWaitingQueueBookingCanBeCancelled() {
        when(booking.getSlot()).thenReturn(slot);
        when(slot.getStatus()).thenReturn(SlotStatus.BOOKED);
        when(booking.getId()).thenReturn(bookingId);
        when(slot.getId()).thenReturn(slotId);
        when(bookingRepository.cancelIfActive(eq(bookingId), isNull(), isNull())).thenReturn(1);

        newService().cancel(booking);

        verify(slot).setStatus(SlotStatus.OPEN);
        verify(eventPublisher).publishEvent(any(BookingCancelledEvent.class));
    }

    @Test
    void nonBookedSlotStatusIsRejected() {
        when(booking.getSlot()).thenReturn(slot);
        when(slot.getStatus()).thenReturn(SlotStatus.OPEN);

        assertThatThrownBy(() -> newService().cancel(booking)).isInstanceOf(BookingNotCancellableException.class);

        verify(bookingRepository, never()).cancelIfActive(any(), any(), any());
    }

    @Test
    void losingTheDataLayerRaceIsRejectedNotSilentlyTreatedAsSuccess() {
        when(booking.getSlot()).thenReturn(slot);
        when(slot.getStatus()).thenReturn(SlotStatus.BOOKED);
        when(booking.getId()).thenReturn(bookingId);
        when(bookingRepository.cancelIfActive(eq(bookingId), isNull(), isNull())).thenReturn(0);

        assertThatThrownBy(() -> newService().cancel(booking)).isInstanceOf(BookingNotCancellableException.class);

        verify(slotRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void successfulCancellationReopensTheSlotAndPublishesTheEvent() {
        when(booking.getSlot()).thenReturn(slot);
        when(slot.getStatus()).thenReturn(SlotStatus.BOOKED);
        when(booking.getId()).thenReturn(bookingId);
        when(slot.getId()).thenReturn(slotId);
        when(bookingRepository.cancelIfActive(eq(bookingId), isNull(), isNull())).thenReturn(1);

        Booking result = newService().cancel(booking);

        assertThat(result).isSameAs(booking);
        verify(slot).setStatus(SlotStatus.OPEN);
        verify(slotRepository).save(slot);
        verify(booking).setStatus(com.cms.booking.domain.BookingStatus.CANCELLED);

        ArgumentCaptor<BookingCancelledEvent> eventCaptor = ArgumentCaptor.forClass(BookingCancelledEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().bookingId()).isEqualTo(bookingId);
        assertThat(eventCaptor.getValue().slotId()).isEqualTo(slotId);
    }

    /** 057-day-sheet-status-overhaul research.md Decision 4: extends BOOKED-only eligibility to also allow APPEARED, exercising the exact same cancelIfActive/event-publish path unchanged. */
    @Test
    void appearedSlotIsNowCancellableUsingTheIdenticalPath() {
        when(booking.getSlot()).thenReturn(slot);
        when(slot.getStatus()).thenReturn(SlotStatus.APPEARED);
        when(booking.getId()).thenReturn(bookingId);
        when(slot.getId()).thenReturn(slotId);
        when(bookingRepository.cancelIfActive(eq(bookingId), isNull(), isNull())).thenReturn(1);

        Booking result = newService().cancel(booking);

        assertThat(result).isSameAs(booking);
        verify(slot).setStatus(SlotStatus.OPEN);
        verify(eventPublisher).publishEvent(any(BookingCancelledEvent.class));
    }

    @Test
    void noShowSlotStatusRemainsRejected() {
        when(booking.getSlot()).thenReturn(slot);
        when(slot.getStatus()).thenReturn(SlotStatus.NO_SHOW);

        assertThatThrownBy(() -> newService().cancel(booking)).isInstanceOf(BookingNotCancellableException.class);

        verify(bookingRepository, never()).cancelIfActive(any(), any(), any());
    }

    @Test
    void completedSlotStatusRemainsRejected() {
        when(booking.getSlot()).thenReturn(slot);
        when(slot.getStatus()).thenReturn(SlotStatus.COMPLETED);

        assertThatThrownBy(() -> newService().cancel(booking)).isInstanceOf(BookingNotCancellableException.class);

        verify(bookingRepository, never()).cancelIfActive(any(), any(), any());
    }
}
