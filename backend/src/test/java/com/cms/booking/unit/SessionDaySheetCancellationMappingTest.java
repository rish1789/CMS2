package com.cms.booking.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cms.booking.dto.SessionDaySheetResponse;
import com.cms.scheduling.domain.ScheduleMode;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.SessionCancellation;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 065-phase1-stabilization (owner decision 3): the day sheet says whether the session is
 * cancelled and which ranges are, so staff see why a slot can't be booked instead of discovering
 * it through a refused booking.
 */
class SessionDaySheetCancellationMappingTest {

    private Session session;

    @BeforeEach
    void aSession() {
        session = mock(Session.class, RETURNS_DEEP_STUBS);
        when(session.getId()).thenReturn(UUID.randomUUID());
        when(session.getDoctorProfile().getId()).thenReturn(UUID.randomUUID());
        when(session.getDoctorProfile().getAccount().getName()).thenReturn("Dr. Rao");
        when(session.getSessionDate()).thenReturn(LocalDate.of(2026, 10, 1));
        when(session.getMode()).thenReturn(ScheduleMode.FIXED_TIME);
    }

    @Test
    void aSessionWithNoCancellationIsNeitherCancelledNorHasRanges() {
        SessionDaySheetResponse response = SessionDaySheetResponse.of(session, List.of(), List.of());

        assertThat(response.cancelled()).isFalse();
        assertThat(response.cancelledRanges()).isEmpty();
    }

    @Test
    void aWholeRecordMarksTheSessionCancelledAndRangesAreListedInOrder() {
        UUID by = UUID.randomUUID();
        List<SessionCancellation> records = List.of(
                SessionCancellation.range(session, LocalTime.of(12, 0), null, by, Instant.EPOCH),
                SessionCancellation.whole(session, by, Instant.EPOCH),
                SessionCancellation.range(session, LocalTime.of(9, 0), LocalTime.of(10, 0), by, Instant.EPOCH));

        SessionDaySheetResponse response = SessionDaySheetResponse.of(session, List.of(), records);

        assertThat(response.cancelled()).isTrue();
        assertThat(response.cancelledRanges())
                .containsExactly(
                        new SessionDaySheetResponse.CancelledRange(LocalTime.of(9, 0), LocalTime.of(10, 0)),
                        new SessionDaySheetResponse.CancelledRange(LocalTime.of(12, 0), null));
    }
}
