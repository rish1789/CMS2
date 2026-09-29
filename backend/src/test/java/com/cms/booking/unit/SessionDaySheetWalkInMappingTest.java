package com.cms.booking.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cms.booking.domain.Booking;
import com.cms.booking.domain.BookingSource;
import com.cms.booking.domain.VisitReason;
import com.cms.booking.dto.SessionDaySheetResponse;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 063-front-desk-walk-in (US3, FR-019, contract section 3, tasks.md T028): the Day Sheet carries
 * what the front desk needs to show a walk-in - its visit reason, and when it was sent in and
 * finished - alongside the existing walk-in flag.
 */
class SessionDaySheetWalkInMappingTest {

    @Test
    void aWalkInRowCarriesItsVisitReasonAndVisitTimes() {
        Slot slot = new Slot(mock(Session.class), 1);
        ReflectionTestUtils.setField(slot, "id", UUID.randomUUID());
        slot.setStatus(SlotStatus.BOOKED);
        slot.setStatus(SlotStatus.APPEARED);
        slot.setStatus(SlotStatus.COMPLETED);
        Booking booking = mock(Booking.class, RETURNS_DEEP_STUBS);
        when(booking.getSource()).thenReturn(BookingSource.WALK_IN);
        when(booking.getVisitReason()).thenReturn(VisitReason.OTHER);
        when(booking.getVisitReasonDetail()).thenReturn("Dizziness");

        SessionDaySheetResponse.SlotDetail row =
                SessionDaySheetResponse.SlotDetail.of(slot, SessionDaySheetResponse.BookingDetail.from(booking));

        assertThat(row.startTime()).isNull();
        assertThat(row.tokenNumber()).isEqualTo(1);
        assertThat(row.appearedAt()).isNotNull();
        assertThat(row.completedAt()).isNotNull();
        assertThat(row.booking().isWalkIn()).isTrue();
        assertThat(row.booking().visitReason()).isEqualTo(VisitReason.OTHER);
        assertThat(row.booking().visitReasonDetail()).isEqualTo("Dizziness");
    }

    @Test
    void aBookedVisitHasNoVisitReason() {
        Booking booking = mock(Booking.class, RETURNS_DEEP_STUBS);
        when(booking.getSource()).thenReturn(BookingSource.SCHEDULED);
        when(booking.getVisitReason()).thenReturn(null);
        when(booking.getVisitReasonDetail()).thenReturn(null);

        SessionDaySheetResponse.BookingDetail detail = SessionDaySheetResponse.BookingDetail.from(booking);

        assertThat(detail.isWalkIn()).isFalse();
        assertThat(detail.visitReason()).isNull();
    }
}
